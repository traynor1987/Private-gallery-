// Copyright Private Gallery. All rights reserved.
// Independent, literal single-context CLIP conditioning for the two catalog models.
// Tensor contract: SD1.5 / Stability SDXL base; see feasibility audit for sources.
#include "tokenizers/clip_tokenizer.h"
struct PgClipConditioner final : Conditioner {
    bool xl;
    std::vector<std::shared_ptr<CLIPTextModelRunner>> runners;
    std::vector<std::string> prefixes;
    CLIPTokenizer tokenizer;
    PgClipConditioner(ggml_backend_t backend, const String2TensorStorage& storage,
                      SDVersion version, std::shared_ptr<RunnerWeightManager> manager)
        : xl(version == VERSION_SDXL) {
        if (version != VERSION_SD1 && !xl) throw std::runtime_error("Unsupported local model architecture");
        prefixes.push_back("cond_stage_model.transformer.text_model");
        runners.push_back(std::make_shared<CLIPTextModelRunner>(backend, storage, prefixes[0],
                          OPENAI_CLIP_VIT_L_14, !xl, false, manager));
        if (xl) {
            prefixes.push_back("cond_stage_model.1.transformer.text_model");
            runners.push_back(std::make_shared<CLIPTextModelRunner>(backend, storage, prefixes[1],
                              OPEN_CLIP_VIT_BIGG_14, false, false, manager));
        }
    }
    SDCondition get_learned_condition(int threads, const ConditionerParams& p) override {
        std::vector<int> content;
        if (!tokenizer.encode(p.text, content) || content.size() > 75)
            throw std::runtime_error("Local prompt exceeds the model context");
        std::vector<int32_t> ids(77, 49407);
        ids[0] = 49406;
        std::copy(content.begin(), content.end(), ids.begin() + 1);
        const size_t eos = content.size() + 1;
        auto input = sd::Tensor<int32_t>::from_vector(ids);
        auto hidden = runners[0]->compute(threads, input, 0, nullptr, eos, false, xl ? 2 : 1);
        if (hidden.numel() != 768 * 77) throw std::runtime_error("Invalid CLIP output");
        SDCondition result;
        if (!xl) { result.c_crossattn = std::move(hidden); return result; }
        std::fill(ids.begin() + eos + 1, ids.end(), 0);
        input = sd::Tensor<int32_t>::from_vector(ids);
        auto big = runners[1]->compute(threads, input, 0, nullptr, eos, false, 2);
        auto pooled = runners[1]->compute(threads, input, 0, nullptr, eos, true, 1);
        if (big.numel() != 1280 * 77 || pooled.numel() != 1280)
            throw std::runtime_error("Invalid SDXL conditioning output");
        result.c_crossattn = sd::Tensor<float>({2048, 77, 1});
        for (int token = 0; token < 77; ++token) {
            auto out = result.c_crossattn.data() + token * 2048;
            std::copy_n(hidden.data() + token * 768, 768, out);
            std::copy_n(big.data() + token * 1280, 1280, out + 768);
        }
        result.c_vector = sd::Tensor<float>({2816});
        std::copy_n(pooled.data(), 1280, result.c_vector.data());
        if (p.zero_out_masked && p.text.empty()) {
            result.c_crossattn.fill_(0);
            std::fill_n(result.c_vector.data(), 1280, 0.0f);
        }
        const int coordinates[] = {p.height, p.width, 0, 0, p.height, p.width};
        for (int coordinate = 0; coordinate < 6; ++coordinate) {
            float* out = result.c_vector.data() + 1280 + coordinate * 256;
            for (int frequency = 0; frequency < 128; ++frequency) {
                double phase = coordinates[coordinate] * std::pow(10000.0, -frequency / 128.0);
                out[frequency] = std::cos(phase);
                out[frequency + 128] = std::sin(phase);
            }
        }
        return result;
    }
    void get_param_tensors(std::map<std::string, ggml_tensor*>& tensors) override {
        for (size_t i = 0; i < runners.size(); ++i) runners[i]->get_param_tensors(tensors, prefixes[i]);
    }
    void get_layer_split_param_tensors(std::map<std::string, ggml_tensor*>& tensors) override { get_param_tensors(tensors); }
    void set_flash_attention_enabled(bool value) override { for (auto& r : runners) r->set_flash_attention_enabled(value); }
    void set_scale_overrides(float linear, float attention) override { for (auto& r : runners) r->set_scale_overrides(linear, attention); }
    void set_weight_adapter(const std::shared_ptr<WeightAdapter>& adapter) override { for (auto& r : runners) r->set_weight_adapter(adapter); }
    void set_max_graph_vram_bytes(size_t value) override { for (auto& r : runners) r->set_max_graph_vram_bytes(value); }
    void set_runtime_backends(const std::vector<ggml_backend_t>& value) override { for (auto& r : runners) r->set_runtime_backends(value); }
    void set_graph_cut_layer_split_enabled(bool value) override { for (auto& r : runners) r->set_graph_cut_layer_split_enabled(value); }
    void set_graph_cut_layer_split_backend_vram_limits(const std::vector<size_t>& value) override { for (auto& r : runners) r->set_graph_cut_layer_split_backend_vram_limits(value); }
    void runner_end() override { for (auto& r : runners) r->runner_end(); }
};
