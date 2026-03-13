# Batch Import Guide

## Overview

The interview-guide application supports batch importing JSONL Q&A files and PDF books into pgvector via DashScope text-embedding-v3 (1024 dimensions).

## Features

✅ **JSONL Import**: Batch import Q&A files from directory  
✅ **PDF Books Import**: Recursive scan and import PDF files  
✅ **DashScope Embeddings**: text-embedding-v3 with 1024 dimensions  
✅ **pgvector Storage**: PostgreSQL vector database  
✅ **Redis Stream**: Async processing pipeline  
✅ **Deduplication**: SHA-256 hash-based duplicate detection  
✅ **Tika Integration**: PDF/DOCX/PPTX parsing  

## API Endpoints

### 1. Import JSONL Q&A Files

```bash
POST /api/knowledgebase/import/jsonl
Content-Type: application/json

{
  "directory": "/path/to/jsonl/directory",
  "category": "DevOps Interview"  // Optional
}
```

**Response:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalFiles": 40,
    "imported": 35,
    "skipped": 5,
    "failed": 0,
    "importedFiles": [
      {
        "knowledgeBaseId": 1,
        "name": "DevOps RAG Dataset",
        "category": "DevOps Interview",
        "originalFilename": "devops_rag_dataset_405.jsonl"
      }
    ],
    "errors": []
  }
}
```

### 2. Import PDF Books

```bash
POST /api/knowledgebase/import/books
Content-Type: application/json

{
  "directory": "/home/calelin/books",
  "useSubdirAsCategory": true,
  "defaultCategory": "Books"  // Optional
}
```

**Response:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalFiles": 16,
    "imported": 16,
    "skipped": 0,
    "failed": 0,
    "importedFiles": [...],
    "errors": []
  }
}
```

## JSONL Format Support

The parser supports multiple field variants:

**English Fields:**
- `question`, `answer`, `key_points`, `example`, `keywords`

**Chinese Fields:**
- `question_zh`, `detailed_answer_zh`, `short_answer_zh`, `key_points_zh`, `example_zh`

**Priority:**
- Answer: `detailed_answer_zh` > `short_answer_zh` > `answer`
- Question: `question_zh` > `question`
- Key Points: `key_points_zh` > `key_points`

**Example JSONL Entry:**
```json
{
  "id": "devops_001",
  "category": "devops",
  "question_zh": "什么是 DevOps?",
  "detailed_answer_zh": "DevOps是...",
  "key_points_zh": ["定义", "作用", "场景"],
  "example_zh": "例如：CI/CD...",
  "keywords": ["DevOps", "CI/CD"]
}
```

## Processing Flow

```
1. Scan Directory
   ↓
2. For each file:
   - Calculate SHA-256 hash
   - Check duplicate (existsByFileHash)
   ↓
3. Parse Content
   - JSONL: Parse Q&A entries → Concatenate
   - PDF: Tika extraction
   ↓
4. Save Entity
   - Create KnowledgeBaseEntity
   - Status: PENDING
   ↓
5. Send to Redis Stream
   - stream:vectorize
   ↓
6. Consumer Processes
   - Status: PROCESSING
   - Chunk (800 tokens, 100 overlap)
   - Embed (DashScope text-embedding-v3)
   - Store in pgvector (1024 dims)
   - Status: COMPLETED + chunkCount
```

## Configuration

**application.yml:**
```yaml
spring:
  ai:
    openai:
      embedding:
        options:
          model: text-embedding-v3
    vectorstore:
      pgvector:
        dimensions: 1024
        index-type: HNSW
        distance-type: COSINE_DISTANCE
```

**Chunking:**
- Chunk size: 800 tokens
- Overlap: 100 tokens
- Splitter: TokenTextSplitter

**Batch Size:**
- DashScope API limit: 10 chunks per batch
- Automatic batching in VectorService

## Testing

### 1. Start Services

```bash
docker-compose up -d
```

### 2. Import JSONL

```bash
curl -X POST http://localhost:8080/api/knowledgebase/import/jsonl \
  -H "Content-Type: application/json" \
  -d '{
    "directory": "/home/calelin/dev/RAG/kubernetes_rag/data/devops_rag_kb",
    "category": "DevOps Interview"
  }'
```

### 3. Import Books

```bash
curl -X POST http://localhost:8080/api/knowledgebase/import/books \
  -H "Content-Type: application/json" \
  -d '{
    "directory": "/home/calelin/books",
    "useSubdirAsCategory": true,
    "defaultCategory": "Books"
  }'
```

### 4. Check Status

```bash
curl http://localhost:8080/api/knowledgebase/list?vectorStatus=COMPLETED
```

## Architecture

**Components:**

1. **KnowledgeBaseEntity**: JPA entity with fileHash, vectorStatus, chunkCount
2. **KnowledgeBasePersistenceService**: Save entities, deduplication
3. **KnowledgeBaseParseService**: Tika parsing, JSONL parsing
4. **KnowledgeBaseVectorService**: Chunking, embedding, pgvector storage
5. **VectorizeStreamProducer**: Redis Stream producer
6. **VectorizeStreamConsumer**: Async consumer (Redisson)
7. **KnowledgeBaseBatchImportService**: Orchestrates import flow
8. **KnowledgeBaseController**: REST API endpoints

## Status Flow

```
PENDING → PROCESSING → COMPLETED
                ↓
             FAILED
```

## Error Handling

- **Duplicate files**: Skipped (SHA-256 hash check)
- **Parse errors**: Logged, file skipped
- **Vectorization errors**: Status set to FAILED, error message stored
- **Stream errors**: Retry mechanism (max 3 retries)

## Performance

- **Batch processing**: Async via Redis Stream
- **Chunking**: 800 tokens per chunk, 100 overlap
- **Embedding**: DashScope text-embedding-v3 (1024 dims)
- **Storage**: pgvector with HNSW index
- **Deduplication**: SHA-256 hash-based

## Monitoring

Check import status:
```bash
# List all knowledge bases
GET /api/knowledgebase/list

# Filter by status
GET /api/knowledgebase/list?vectorStatus=COMPLETED

# Get statistics
GET /api/knowledgebase/stats
```

## Notes

- Files are deduplicated by SHA-256 hash
- Vectorization happens asynchronously via Redis Stream
- Status updates automatically (PENDING → PROCESSING → COMPLETED)
- chunkCount is set after successful vectorization
- Supports both English and Chinese field variants in JSONL
