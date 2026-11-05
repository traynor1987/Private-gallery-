# Nova Home layout JSON version1

UTF-8, max524288bytes. Root has exactly format=`nova-home-layout`, version=1, layout, widgets, theme, reducedMotion and demoLock. Unknown fields or versions are rejected.

Layout fields: pages (1–8 identifier arrays), dock (max5), folders (id/name/member identifiers), installedIds (max200 web:SHA256-prefix identifiers), drawer order, columns3–6, iconSize44–76 and wallpaper (builtin, canonical local image filename or empty, bounded zoom/x/y/blur/dim). No arbitrary image paths, file extraction, network URLs or remote fetch is permitted. Missing local wallpaper images use the built-in gradient. Existing installed apps remain; known catalogue identifiers can be resolved locally. Custom destination identifiers never export their URLs and resolve only when already installed.

Widgets: max64 non-overlapping page-local slots, four columns/24 rows, id/page/x/y/w/h/active/items. Max8 items per stack; id/type and blank config only. Widget note text and all sensitive configuration are intentionally omitted. Unknown future widget types produce safe placeholders; incompatible geometry is rejected before restore.

Restore validates bytes/schema/types/numeric bounds/counts/IDs/geometry before presenting a preview. User confirms target demonstration profile. It snapshots Home and widget state into preview-private recovery preferences before committing either store. Any failure triggers rollback; interrupted transactions recover on next launch. Snapshots can contain existing private layout/Quick Note configuration and must be encrypted under a future production Space key. They are never included in exported JSON. No cookie, authentication, journal, mood, search history, media URI or production key is accepted by this format.
