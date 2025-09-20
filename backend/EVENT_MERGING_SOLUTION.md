# Event Merging Solution

## 🚨 Problem Addressed

**Cross-Batch Duplication Issue**: When similar articles are processed in different 15-article batches, the AI creates
separate events for what should be the same real-world occurrence, leading to fragmented information and duplicate
events.

## 🛠️ Solution Overview

The Event Merging System introduces a **post-processing consolidation step** that uses AI to identify and merge
semantically similar events created across different batches.

## 🏗️ Architecture Components

### 1. **EventMergingService**

- **Core Logic**: AI-powered similarity analysis of recent events
- **AI Prompt**: Specialized Bengali prompt for event deduplication
- **Batch Processing**: Analyzes events in configurable time windows
- **Confidence Scoring**: Only merges events with >0.7 confidence score

### 2. **New DTOs**

- `EventMergeCandidate`: Represents groups of events to be merged
- `EventForMergeAnalysis`: Simplified event data for AI processing

### 3. **Extended Repository**

- `findByCreatedAtAfterAndIsProcessed()`: Find recent unprocessed events
- `countByCreatedAtAfterAndIsProcessed()`: Statistics for merge analysis

### 4. **Controller Endpoints**

- `POST /api/events/merge-similar`: Merge events from last 48 hours
- `POST /api/events/merge-recent?hoursBack=X`: Custom time window
- `POST /api/events/process-all`: Updated pipeline with merging step

## 🔄 Updated Pipeline Workflow

```
Original Flow:
Articles → Batch Mapping → Aggregation

New Flow:
Articles → Batch Mapping → Event Merging → Aggregation
```

### Detailed Steps:

1. **Event Mapping**: Create events from article batches (existing)
2. **Event Merging**: Consolidate duplicate events (NEW!)
3. **Event Aggregation**: Create unified summaries (existing)

## 🤖 AI Merging Logic

### Input Analysis:

- **Temporal Proximity**: Events created within similar timeframes
- **Semantic Similarity**: Title and content analysis
- **Event Type Matching**: Category consistency
- **Article Count Correlation**: Similar coverage patterns

### Decision Criteria:

- **Minimum Confidence**: 0.7+ required for merging
- **Minimum Group Size**: 2+ events required
- **Time Window**: Configurable (default 48 hours)

### Bengali AI Prompt:

```bengali
আপনি একজন বিশেষজ্ঞ সংবাদ বিশ্লেষক। নিচে দেওয়া ইভেন্টগুলো বিশ্লেষণ করে একই বাস্তব ঘটনা সম্পর্কিত ইভেন্টগুলো চিহ্নিত করুন এবং সেগুলো একত্রিত করার পরামর্শ দিন।
```

## 📊 Merging Process

### Event Selection:

```sql
SELECT e FROM Event e 
WHERE e.createdAt >= :since 
AND e.isProcessed = false 
ORDER BY e.createdAt DESC
```

### Merge Execution:

1. **Primary Event Selection**: First event becomes the master
2. **Article Migration**: Move all mappings to primary event
3. **Metadata Update**: Merge titles, confidence scores, article counts
4. **Cleanup**: Delete merged events, update timestamps

### Confidence Calculation:

```java
// Weighted average of all merged events
totalConfidence = sum(event.confidence * event.articleCount)
newConfidence = totalConfidence / totalArticleCount
```

## 🚀 API Usage Examples

### Trigger Standard Merging (48 hours)

```bash
POST /api/events/merge-similar
```

### Custom Time Window

```bash
POST /api/events/merge-recent?hoursBack=72
```

### Complete Pipeline with Merging

```bash
POST /api/events/process-all
```

### Check Merge Statistics

```bash
GET /api/events/stats
```

## 📈 Expected Results

### Before Merging:

```
Event 1: "বাংলাদেশ অর্থনীতি" (3 articles, batch 1)
Event 2: "অর্থনৈতিক অবস্থা বাংলাদেশ" (2 articles, batch 3)
Event 3: "দেশের আর্থিক পরিস্থিতি" (4 articles, batch 5)
```

### After Merging:

```
Event 1: "বাংলাদেশের বর্তমান অর্থনৈতিক পরিস্থিতি" (9 articles, merged)
```

## 🛡️ Safety Features

1. **High Confidence Threshold**: Only 0.7+ confidence merges executed
2. **Preserve Primary Event**: Maintains original event ID and relationships
3. **Audit Trail**: Mapping method updated to "AI_MERGING"
4. **Rollback Capability**: Transaction-based execution
5. **Error Handling**: Graceful failure with detailed logging

## 🔍 Monitoring & Statistics

### Merge Stats Available:

- `recentUnprocessedEvents24h`: Events needing merge analysis
- `weeklyUnprocessedEvents`: Weekly merge candidates
- `totalUnprocessedEvents`: Overall backlog
- `recommendedMergeAnalysis`: Boolean recommendation flag

## 🎯 Benefits

1. **Eliminates Duplication**: Solves cross-batch fragmentation
2. **Improved Quality**: Better event consolidation
3. **Enhanced Analytics**: More accurate event statistics
4. **Flexible Configuration**: Customizable time windows
5. **Preserves Performance**: Post-processing approach maintains mapping speed

## 🔧 Configuration

### Environment Variables:

- Uses existing `secret.api.key1-6` for AI calls
- Leverages existing API monitoring system
- Integrates with current error logging

### Tunable Parameters:

- **Default Time Window**: 48 hours
- **Confidence Threshold**: 0.7
- **Minimum Group Size**: 2 events
- **Token Estimation**: 4 chars per token (Bengali)

This solution effectively addresses the cross-batch duplication problem while maintaining the existing architecture's
performance and reliability.