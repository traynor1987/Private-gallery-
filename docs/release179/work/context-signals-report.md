# Contextual grouping completion

Original requirement audit found foundation suggestions omitted OCR dates, similar (nonidentical) conversation text and useful existing-incident association choices. Root added bounded raw/extracted date hints, transcript trigram similarities, and explicit ASSIGN suggestions to existing incident based on an overlapping related record. Suggestions never auto-apply. Accepting ASSIGN is one audited reference-only mutation, no incident merge/original change, and unions immutable source lineage. GROUP remains supported; reject persists stable digest. Existing completed group suppression and global256candidate/8192signal bounds remain.

Observed TDD RED: bash scripts/check_evidence179.sh exited1 AssertionError OCR visible date signals missing at contextualSuggestions line100. Implemented then GREEN:124 catalogue/store assertions,21 captured authority checks, all timestamp hints PASS. New contextualSuggestions covers raw OCR time signal reason, nonidentical transcript similarity, existing incident new-reference-only hint, one audited explicit acceptance, original immutability/no-autoapply, and persisted rejection. No Android claim. Afterwards changed suggestion display title to fixed shortlabel to avoid maximumincidenttitle prefix overflow; target identity still explicit incidentId, UI resolves targetname locally. Host rerun covering this finalchange required beforecommit.

Review scope package review179-context-signals.diff. Root UI will present existing targetname and separate approval; editing target goes through explicit owner reassignment, never model output. No other model/store/authority APIs changed.

Final-labelchange host rerun:124catalogue/store +21authority+hints allPASS (exit0).
