-- Create pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create knowledge_base table
CREATE TABLE IF NOT EXISTS knowledge_base (
    id UUID PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    category VARCHAR(200) NOT NULL,
    original_filename VARCHAR(500),
    file_hash VARCHAR(64) NOT NULL UNIQUE,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    storage_key VARCHAR(500),
    storage_url VARCHAR(1000),
    vector_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    chunk_count INTEGER,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    error_message TEXT,
    embedding_model VARCHAR(50),
    embedding_dimension INTEGER
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_file_hash ON knowledge_base(file_hash);
CREATE INDEX IF NOT EXISTS idx_vector_status ON knowledge_base(vector_status);

-- Create document embedding table
CREATE TABLE IF NOT EXISTS document_embedding (
    id SERIAL PRIMARY KEY,
    knowledge_base_id UUID NOT NULL,
    content TEXT NOT NULL,
    generated_at TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id) ON DELETE CASCADE
);

-- Create index on knowledge_base_id
CREATE INDEX IF NOT EXISTS idx_document_embedding_knowledge_base_id ON document_embedding(knowledge_base_id);

-- Insert sample data for testing
--INSERT INTO knowledge_base (id, name, category, original_filename, file_hash, file_size, content_type, vector_status)
--VALUES (
--    gen_random_uuid(),
--    'Sample Document',
--    'Testing',
--    'test.txt',
--    SHA256('test'),
--    10,
--    'text/plain',
--    'PENDING'
--);