"""Small, fail-closed adaptation of the pinned MIT runtime for inherited model FDs.
Also excludes implementations with unresolved upstream copyleft provenance.
"""
from pathlib import Path
import subprocess, sys, shutil
root = Path(sys.argv[1])
header = Path(__file__).with_name('pg_fd_stream.h')
shutil.copyfile(header, root / 'src/pg_fd_stream.h')
def source(path):
    return subprocess.check_output(['git', '-C', str(root), 'show', '19bbbca1c736bbb9538679fc0ae690cb2b46b492:' + path], text=True)
def replace(text, old, new):
    assert text.count(old) == 1, old
    return text.replace(old, new)
for path in ['src/model_io/safetensors_io.cpp', 'src/model_loader.cpp']:
    text = source(path).replace('std::ifstream', 'PgInputStream')
    text = '#include "pg_fd_stream.h"\n' + text
    if path.endswith('model_loader.cpp'):
        text = replace(text, '    if (is_directory(file_path)) {', '    if (pg_is_model(file_path)) {\n        return is_safetensors_file(file_path) && init_from_safetensors_file(file_path, prefix);\n    }\n    if (is_directory(file_path)) {')
    (root / path).write_text(text)
path = 'src/core/util.cpp'
text = '#include "pg_fd_stream.h"\n' + source(path)
text = replace(text, 'int file_descriptor = open(filename.c_str(), O_RDONLY);', 'int file_descriptor = pg_open_read(filename);')
(root / path).write_text(text)
path = 'src/model_loader_files.cpp'
text = '#include "pg_fd_stream.h"\n' + source(path)
text = replace(text, 'bool ModelLoader::read_file_stamp(const std::string& path, FileStamp& stamp) {', '''bool ModelLoader::read_file_stamp(const std::string& path, FileStamp& stamp) {
    if (pg_is_model(path)) {
        struct stat st{};
        if (fstat(pg_model_fd, &st) || !S_ISREG(st.st_mode) || st.st_size <= 0) return false;
        stamp.path = path;
        stamp.size = st.st_size;
        stamp.modified = std::filesystem::file_time_type(std::chrono::duration_cast<std::filesystem::file_time_type::duration>(std::chrono::seconds(st.st_mtim.tv_sec) + std::chrono::nanoseconds(st.st_mtim.tv_nsec)));
        return true;
    }''')
text = replace(text, 'if (!std::filesystem::exists(std::filesystem::u8path(stamp.path), error)) {', 'if (!pg_is_model(stamp.path) && !std::filesystem::exists(std::filesystem::u8path(stamp.path), error)) {')
text = replace(text, 'if (!std::filesystem::exists(std::filesystem::u8path(stamp.path), error) && !error) {', 'if (!pg_is_model(stamp.path) && !std::filesystem::exists(std::filesystem::u8path(stamp.path), error) && !error) {')
(root / path).write_text(text)
# The optional CUDA-compatible RNG cites a separately licensed upstream source.
# Exclude it entirely; Private Gallery uses the independent standard C++ RNG.
for path in ['src/pipeline/diffusion_engine.cpp', 'src/runtime/denoiser.hpp', 'src/pipeline/video.cpp']:
    text = source(path)
    assert '#include "core/rng_philox.hpp"' in text and 'PhiloxRNG' in text
    text = text.replace('#include "core/rng_philox.hpp"', '#include "core/rng.hpp"')
    text = text.replace('PhiloxRNG', 'STDDefaultRNG')
    (root / path).write_text(text)
(root / 'src/core/rng_philox.hpp').unlink(missing_ok=True)
# Exclude the entire AGPL-referenced custom-word conditioner and attention parser.
# The replacement implements only literal, bounded CLIP model inputs.
shutil.copyfile(Path(__file__).with_name('pg_conditioner.hpp'), root / 'src/conditioning/pg_conditioner.hpp')
path = 'src/conditioning/conditioner.hpp'
text = source(path)
weight_start = text.index('static inline sd::Tensor<float> apply_token_weights')
weight_end = text.index('struct ConditionerParams', weight_start)
text = text[:weight_start] + '''// Literal conditioning does not apply prompt syntax weights.
static inline sd::Tensor<float> apply_token_weights(sd::Tensor<float> states, const std::vector<float>&) { return states; }

''' + text[weight_end:]
start = text.index('// ldm.modules.encoders.modules.FrozenCLIPEmbedder')
end = text.index('struct FrozenCLIPVisionEmbedder', start)
text = text[:start] + '#include "pg_conditioner.hpp"\n\n' + text[end:]
(root / path).write_text(text)
path = 'src/core/util.cpp'
text = (root / path).read_text()
start = text.index('// Ref: https://github.com/AUTOMATIC1111/')
end = text.index('static size_t get_utf8_char_len', start)
text = text[:start] + '''// Private Gallery: prompts are literal text, with no attention syntax.
std::vector<std::pair<std::string, float>> parse_prompt_attention(const std::string& text) {
    return {{text, 1.0f}};
}

''' + text[end:]
(root / path).write_text(text)
path = 'src/pipeline/model_builders.cpp'
text = source(path)
start = text.index('            std::map<std::string, std::string> embbeding_map;')
end = text.index('            result.diffusion', start)
text = text[:start] + '''            result.conditioner = std::make_shared<PgClipConditioner>(
                ctx.backends.runtime_backend(SDBackendModule::TE), tensor_storage_map, version, weight_manager);
''' + text[end:]
text = replace(text, '{create_photomaker_extension(), create_pulid_extension()}', '{create_pulid_extension()}')
(root / path).write_text(text)
(root / 'src/extensions/photomaker_extension.cpp').unlink(missing_ok=True)

# Fail closed if any excluded reference reappears in the compiled source.
for path in (root / 'src').rglob('*'):
    if path.suffix in {'.h', '.hpp', '.cpp'}:
        assert 'AUTOMATIC1111' not in path.read_text(), str(path)
# Private Gallery does not ship ControlNet or LLaDA. Their cited sources do not
# establish the permissive grant needed by this proprietary integration.
(root / 'src/model/diffusion/control.hpp').write_text('''#pragma once
#include "core/ggml_runner.h"
// Private Gallery disabled-feature interface. No upstream ControlNet implementation.
struct ControlNet : GGMLRunner {
    using GGMLRunner::GGMLRunner;
    std::string get_desc() override { return "disabled"; }
    void get_param_tensors(std::map<std::string, ggml_tensor*>&) {}
    void free_control_ctx() {}
    template<class... Args> std::optional<std::vector<sd::Tensor<float>>> compute(Args&&...) { return std::nullopt; }
};
''')
path = 'src/pipeline/model_builders.cpp'
text = (root / path).read_text().replace('#include "model/diffusion/llada_image.hpp"', '')
start = text.index('        } else if (sd_version_is_llada_image(version)) {')
end = text.index('        } else if (sd_version_is_boogu_image(version)) {', start)
text = text[:start] + text[end:]
start = text.index('    bool build_control_net_runner(')
end = text.index('    bool build_extension_runners(', start)
text = text[:start] + '''    bool build_control_net_runner(const Context&, std::shared_ptr<ControlNet>& runner) {
        runner.reset();
        return false; // Unsupported feature, never selected by the local catalog.
    }

''' + text[end:]
(root / path).write_text(text)
path = 'src/conditioning/conditioner.hpp'
text = (root / path).read_text().replace('#include "model/te/llada_image_te.hpp"', '')
start = text.index("// LLaDA-Image's text path")
end = text.index('struct LTXAVEmbedder', start)
text = text[:start] + text[end:]
(root / path).write_text(text)
for path in ['src/model/diffusion/llada_image.hpp', 'src/model/te/llada_image_te.hpp']:
    (root / path).unlink(missing_ok=True)
