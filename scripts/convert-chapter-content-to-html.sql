UPDATE edu_chapter
SET content = '<p>' || replace(replace(replace(content, '&', '&amp;'), '<', '&lt;'), '>', '&gt;') || '</p>',
    updated_at = now()
WHERE deleted = 0
  AND content IS NOT NULL
  AND btrim(content) <> ''
  AND content !~ '^\s*<';
