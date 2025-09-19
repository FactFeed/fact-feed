-- FactFeed Database Schema
-- This schema supports the 4-step pipeline: Scrape -> Summarize -> Cluster -> Finalize

-- Table for storing scraped news articles
CREATE TABLE IF NOT EXISTS articles (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    url VARCHAR(1000) UNIQUE NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    scraped_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    initial_summary TEXT,                 -- AI-generated summary for clustering
    status VARCHAR(20) DEFAULT 'NEW',     -- NEW, SUMMARIZED, CLUSTERED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Table for storing similarity clusters of related articles
CREATE TABLE IF NOT EXISTS similarity_clusters (
    id BIGSERIAL PRIMARY KEY,
    article_ids JSONB NOT NULL,           -- Array of original article IDs in this cluster
    final_summary TEXT,                   -- The final, user-facing summary with discrepancies
    key_topics TEXT[],                    -- AI-generated keywords for the cluster (e.g., for tagging)
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, PROCESSING, COMPLETE
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for better performance
CREATE INDEX IF NOT EXISTS idx_articles_source_name ON articles(source_name);
CREATE INDEX IF NOT EXISTS idx_articles_status ON articles(status);
CREATE INDEX IF NOT EXISTS idx_articles_scraped_at ON articles(scraped_at);
CREATE INDEX IF NOT EXISTS idx_articles_published_at ON articles(published_at);
CREATE INDEX IF NOT EXISTS idx_articles_url ON articles(url);

CREATE INDEX IF NOT EXISTS idx_similarity_clusters_status ON similarity_clusters(status);
CREATE INDEX IF NOT EXISTS idx_similarity_clusters_created_at ON similarity_clusters(created_at);
CREATE INDEX IF NOT EXISTS idx_similarity_clusters_updated_at ON similarity_clusters(updated_at);

-- Index for JSONB article_ids for efficient queries
CREATE INDEX IF NOT EXISTS idx_similarity_clusters_article_ids ON similarity_clusters USING GIN(article_ids);