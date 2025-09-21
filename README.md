# FactFeed - AI-Powered News Aggregation Platform

FactFeed is an intelligent news aggregation system that automatically scrapes articles from major Bangladeshi newspapers, summarizes them using AI, maps related articles to events, aggregates information across sources, and identifies discrepancies to provide comprehensive news analysis.

## 🎯 Project Overview

FactFeed solves the problem of information fragmentation across multiple news sources by:
- **Automated Scraping**: Continuously monitors 5 major Bangladeshi news sources
- **AI Summarization**: Uses Google Gemini AI to create concise summaries
- **Event Mapping**: Groups related articles into coherent news events
- **Discrepancy Detection**: Identifies conflicting information across sources
- **Content Aggregation**: Creates unified summaries from multiple perspectives
- **Real-time Dashboard**: Provides an intuitive interface for consuming aggregated news

## 🏗️ Architecture

### Backend (Spring Boot 3.5.5 + Java 21)
- **Web Scraping**: Selenium + JSoup for robust article extraction
- **AI Integration**: Spring AI + Google Gemini 2.5 Flash for content processing
- **Database**: PostgreSQL with JPA/Hibernate
- **API Management**: Multi-key rotation for Gemini API usage monitoring
- **Async Processing**: Parallel scraping and event processing pipelines
- **Scheduled Tasks**: Automated pipeline execution every 4 hours

### Frontend (React 19 + TypeScript + Vite)
- **Modern UI**: Tailwind CSS with responsive design
- **Advanced Features**: Concurrent features, transitions, optimistic updates
- **Real-time Data**: Dynamic dashboard with event filtering and search
- **Accessibility**: Heroicons integration and proper ARIA support

## 🔄 Complete Pipeline Workflow

### 1. **Automated Scraping Phase**
```
For each news source (5 sources):
├── URL Discovery (Selenium WebDriver)
├── Article Extraction (Parallel processing)
├── Duplicate Filtering (URL-based)
└── Database Storage (PostgreSQL)
```

### 2. **AI Summarization Phase**
```
Unsummarized Articles → Batch Processing (15 articles/batch) → Gemini AI → Summary Storage
├── Multi-key API rotation for rate limit management
├── Bengali-optimized prompts for accurate summarization
└── Token usage monitoring and error handling
```

### 3. **Event Mapping Phase**
```
Summarized Articles → AI Clustering → Event Creation → Article-Event Mappings
├── Intelligent grouping of related articles
├── Confidence scoring for event assignments
├── Support for single-article events (minimum confidence)
└── Cross-batch duplicate detection and merging
```

### 4. **Aggregation & Discrepancy Detection**
```
Mapped Events → Content Analysis → Aggregated Summaries + Discrepancy Highlights
├── Multi-source content synthesis
├── Conflict identification between sources
├── Confidence-weighted information merging
└── Quality validation and error handling
```

## 🚀 Getting Started

### Prerequisites
- **Java 21+**
- **Node.js 18+** & npm/yarn
- **PostgreSQL 13+**
- **Google Gemini API keys** (6 keys recommended for optimal rate limiting)
- **Chrome/Chromium** (for Selenium WebDriver)

### Environment Setup

1. **Clone the repository**
```bash
git clone https://github.com/FactFeed/fact-feed.git
cd fact-feed
```

2. **Backend Configuration**
Create `backend/src/main/resources/application.properties`:
```properties
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/factfeed
spring.datasource.username=postgres
spring.datasource.password=your_password

# AI Configuration (6 API keys for optimal rate limiting)
secret.api.key1=${GEMINI_KEY_Z1}
secret.api.key2=${GEMINI_KEY_Z2}
secret.api.key3=${GEMINI_KEY_Z3}
secret.api.key4=${GEMINI_KEY_Z4}
secret.api.key5=${GEMINI_KEY_C5}
secret.api.key6=${GEMINI_KEY_C1}

# Spring AI Configuration
spring.ai.google.genai.api-key=${GEMINI_KEY_Z1}
spring.ai.google.genai.chat.options.model=gemini-2.5-flash

# Scraping Configuration
article.scraping.thread-pool-size=10
app.startup.auto-scrape=true
app.startup.max-urls-per-source=20
```

3. **Set Environment Variables**
```bash
export GEMINI_KEY_Z1="your_gemini_api_key_1"
export GEMINI_KEY_Z2="your_gemini_api_key_2"
export GEMINI_KEY_Z3="your_gemini_api_key_3"
export GEMINI_KEY_Z4="your_gemini_api_key_4"
export GEMINI_KEY_C5="your_gemini_api_key_5"
export GEMINI_KEY_C1="your_gemini_api_key_6"
```

4. **Database Setup**
```sql
CREATE DATABASE factfeed;
-- Tables will be auto-created by Hibernate DDL
```

### Running the Application

#### Backend (Spring Boot)
```bash
cd backend
./gradlew bootRun
```
Backend will be available at: http://localhost:8080

#### Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev
```
Frontend will be available at: http://localhost:5173

## 📚 API Endpoints

### 🕷️ Scraping Operations
```http
POST /api/scraping/full/all                    # Scrape all sources
POST /api/scraping/full/{source}               # Scrape specific source
GET  /api/discovery/urls/{source}              # Discover URLs for source
```

### 🤖 AI Services
```http
POST /api/summarization/summarize-all          # Summarize all unsummarized articles
GET  /api/summarization/stats                  # Get summarization statistics
POST /api/events/map-all                       # Map articles to events
POST /api/events/merge-similar                 # Merge duplicate events
POST /api/aggregation/process-all              # Generate aggregated summaries
```

### 🌐 Frontend API
```http
GET  /api/frontend/events                      # Get paginated events with articles
GET  /api/frontend/events/{id}                 # Get detailed event view
GET  /api/frontend/events/recent               # Get recent events
GET  /api/frontend/events/with-discrepancies   # Get events with conflicts
GET  /api/frontend/dashboard/stats             # Get dashboard statistics
```

### 📊 Admin & Monitoring
```http
POST /api/admin/startup/trigger                # Trigger complete pipeline
GET  /api/admin/status                         # Get system status
GET  /api/monitoring/api-usage                 # Monitor API consumption
```

## 🗃️ Database Schema

### Core Tables
- **`articles`** - Scraped news articles with full content and summaries
- **`events`** - Grouped news events with aggregated information
- **`article_event_mappings`** - Many-to-many relationship with confidence scores
- **`api_usage_logs`** - API consumption tracking for all Gemini operations

### Key Relationships
```
Articles (1) ↔ (N) ArticleEventMappings (N) ↔ (1) Events
```

## 🎛️ Configuration Options

### Scraping Configuration
```properties
# Thread pool for parallel article scraping
article.scraping.thread-pool-size=10

# Auto-start pipeline on application boot
app.startup.auto-scrape=true
app.startup.scrape-delay=30
app.startup.max-urls-per-source=20
```

### AI Processing
- **Batch Size**: 15 articles per AI request (optimized for token limits)
- **API Key Rotation**: Automatic rotation across 6 keys
- **Rate Limiting**: Built-in monitoring and request throttling
- **Token Estimation**: ~4 characters per token for Bengali text

### Scheduling
- **Full Pipeline**: Every 4 hours (scraping + processing)
- **Light Refresh**: Every hour (3 URLs per source + processing)
- **Auto-startup**: 30-second delay after application boot

## 🔍 Key Features

### 🚀 Automated Pipeline
- **Continuous Monitoring**: 24/7 automated news collection
- **Intelligent Scheduling**: Balanced between freshness and resource usage
- **Error Recovery**: Graceful handling of source failures and API limits
- **Scalable Architecture**: Configurable thread pools and batch sizes

### 🤖 Advanced AI Integration
- **Multi-Model Support**: Google Gemini 2.5 Flash integration
- **Bengali Optimization**: Native support for Bengali text processing
- **Batch Processing**: Efficient token usage through grouped requests
- **Quality Assurance**: Validation and error correction for AI responses

### 📊 Comprehensive Analytics
- **Event Statistics**: Processing rates, confidence scores, article counts
- **API Monitoring**: Token consumption, rate limiting, error tracking
- **Source Performance**: Scraping success rates, content quality metrics
- **Pipeline Health**: Real-time status of all processing stages

### 🌍 Modern Frontend
- **Responsive Design**: Works seamlessly across devices
- **Real-time Updates**: Live dashboard with automatic refresh
- **Advanced Search**: Full-text search across events and articles
- **Discrepancy Highlighting**: Visual identification of conflicting information

## 🛠️ Development

### Code Quality
- **Lefthook**: Git hooks for automated code formatting
- **Biome**: ESLint + Prettier alternative for frontend
- **Spring Boot DevTools**: Hot reload for backend development
- **TypeScript**: Full type safety for frontend

### Pre-Commit Setup
1. **Install Dependencies**: From the root of the project, run `npm install`
2. **Set Up Pre-Commit Hooks**: Run `npx lefthook install` from the root directory

### Testing
- **Unit Tests**: Comprehensive coverage for core business logic
- **Integration Tests**: End-to-end pipeline testing
- **API Testing**: Automated testing of all REST endpoints

### Monitoring
- **Actuator**: Spring Boot health checks and metrics
- **Logging**: Structured logging with proper error tracking
- **Performance**: Built-in monitoring for scraping and AI operations

## 📈 Performance Specifications

### Scraping Performance
- **Parallel Processing**: 10 concurrent article extractions
- **Source Coverage**: 7 major Bangladeshi newspapers
- **Update Frequency**: Every 4 hours (full) + hourly (incremental)
- **Deduplication**: URL-based filtering to prevent reprocessing

### AI Processing
- **Summarization**: ~15 articles per batch, ~2-3 seconds per batch
- **Event Mapping**: Processes all unmapped articles in single operation
- **Aggregation**: Per-event processing with confidence-weighted results
- **API Efficiency**: Multi-key rotation maintains 95%+ uptime

### Data Volume
- **Daily Articles**: ~100-200 new articles across all sources
- **Events Created**: ~20-40 events per day after deduplication
- **Storage Growth**: ~10-20MB per day including images and content

## 🔧 Troubleshooting

#### API Rate Limiting
- **Multiple Keys**: Use 6 different Gemini API keys
- **Error Handling**: Automatic key rotation on rate limit detection
- **Monitoring**: Check `/api/monitoring/api-usage` for consumption patterns

#### Database Connectivity
```sql
-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE factfeed TO postgres;

-- Check connection
SELECT version();
```

#### Frontend Build Issues
```bash
# Clear cache and reinstall
rm -rf node_modules package-lock.json
npm install
npm run build
```

## 🤝 Contributing

1. **Fork the repository**
2. **Create feature branch**: `git checkout -b feature/amazing-feature`
3. **Commit changes**: `git commit -m 'Add amazing feature'`
4. **Push to branch**: `git push origin feature/amazing-feature`
5. **Open Pull Request**

### Development Guidelines
- Follow existing code style (enforced by Biome/Lefthook)
- Add tests for new features
- Update documentation for API changes
- Ensure all sources remain supported

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

- **Issues**: [GitHub Issues](https://github.com/FactFeed/fact-feed/issues)
- **Documentation**: [Wiki](https://github.com/FactFeed/fact-feed/wiki)
- **Discussions**: [GitHub Discussions](https://github.com/FactFeed/fact-feed/discussions)

## 🏆 Acknowledgments

- **Google Gemini AI**: For advanced Bengali text processing capabilities
- **Bangladeshi News Sources**: For providing comprehensive news coverage
- **Spring Boot & React Communities**: For excellent frameworks and documentation
- **Open Source Contributors**: For tools and libraries that made this possible