-- Индекс для колонки `path` длиной до 255 символов
CREATE INDEX idx_path ON page(path(255));

-- Уникальный индекс для сочетания колонок `lemma` и `web_site_id`
CREATE UNIQUE INDEX idx_lemma_website ON lemma (lemma, site_id);
