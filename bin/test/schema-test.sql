-- Test schema for H2 database - updated to match Article entity
CREATE TABLE IF NOT EXISTS articles (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    url VARCHAR(1000) NOT NULL UNIQUE,
    content TEXT,
    source_name VARCHAR(100) NOT NULL,
    published_at TIMESTAMP,
    scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    initial_summary TEXT,
    status VARCHAR(20) DEFAULT 'NEW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS similarity_clusters (
    id BIGSERIAL PRIMARY KEY,
    cluster_hash VARCHAR(64) NOT NULL UNIQUE,
    representative_article_id BIGINT REFERENCES articles(id),
    article_ids VARCHAR(1000),
    similarity_score DECIMAL(3,2) DEFAULT 0.0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_articles_source_name ON articles(source_name);
CREATE INDEX IF NOT EXISTS idx_articles_published_at ON articles(published_at);
CREATE INDEX IF NOT EXISTS idx_articles_scraped_at ON articles(scraped_at);
CREATE INDEX IF NOT EXISTS idx_articles_url ON articles(url);