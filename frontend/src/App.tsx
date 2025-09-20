import React, { useState, useEffect } from 'react';
import { MagnifyingGlassIcon, NewspaperIcon, GlobeAltIcon, ExclamationTriangleIcon, ClockIcon, EyeIcon } from '@heroicons/react/24/outline';
import { SparklesIcon, DocumentTextIcon } from '@heroicons/react/24/solid';

interface Article {
  id: number;
  title: string;
  source: string;
  url: string;
  publishedAt: string;
  summary: string;
  confidenceScore: number;
}

interface Event {
  id: number;
  title: string;
  eventType: string;
  aggregatedSummary: string;
  discrepancies: string;
  confidenceScore: number;
  articleCount: number;
  eventDate: string;
  createdAt: string;
  hasDiscrepancies: boolean;
  articles: Article[];
}

interface Discrepancy {
  id: number;
  title: string;
  eventType: string;
  aggregatedSummary: string;
  discrepancies: string;
  confidenceScore: number;
  articleCount: number;
  eventDate: string;
  createdAt: string;
  hasDiscrepancies: boolean;
  articles: Article[];
}

const App: React.FC = () => {
  const [events, setEvents] = useState<Event[]>([]);
  const [discrepancies, setDiscrepancies] = useState<Discrepancy[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'events' | 'discrepancies'>('events');
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      setLoading(true);
      console.log('Fetching data...');
      
      // Fetch events from frontend controller
      console.log('Fetching events from API...');
      const eventsResponse = await fetch('http://localhost:8080/api/frontend/events', {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      });
      console.log('Events response status:', eventsResponse.status);
      
      if (!eventsResponse.ok) {
        throw new Error(`HTTP error! status: ${eventsResponse.status}`);
      }
      
      const eventsData = await eventsResponse.json();
      console.log('Events data received:', eventsData);
      
      // The API returns a paginated response with events array
      const eventsList = eventsData.events || [];
      setEvents(eventsList);
      console.log('Events set to state:', eventsList.length, 'events');

      // Fetch discrepancies from frontend controller
      console.log('Fetching discrepancies from API...');
      try {
        const discrepanciesResponse = await fetch('http://localhost:8080/api/frontend/events/with-discrepancies', {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json',
          },
        });
        console.log('Discrepancies response status:', discrepanciesResponse.status);
        
        if (discrepanciesResponse.ok) {
          const discrepanciesData = await discrepanciesResponse.json();
          console.log('Discrepancies data received:', discrepanciesData);
          setDiscrepancies(discrepanciesData || []);
        } else {
          console.warn('Failed to fetch discrepancies, continuing without them');
          setDiscrepancies([]);
        }
      } catch (discError) {
        console.warn('Error fetching discrepancies:', discError);
        setDiscrepancies([]);
      }
      
      setLoading(false);
      console.log('Data fetching completed. Events:', events.length, 'Discrepancies:', discrepancies.length);
    } catch (error) {
      console.error('Error fetching data:', error);
      setLoading(false);
    }
  };

  const filteredEvents = events.filter(event =>
    event.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
    event.aggregatedSummary.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('bn-BD', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const getSourceColor = (source: string) => {
    const colors = [
      'bg-blue-100 text-blue-800',
      'bg-green-100 text-green-800',
      'bg-purple-100 text-purple-800',
      'bg-orange-100 text-orange-800',
      'bg-pink-100 text-pink-800',
      'bg-indigo-100 text-indigo-800'
    ];
    return colors[source.length % colors.length];
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-indigo-50">
      {/* Header */}
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-gradient-to-r from-blue-600 to-indigo-600 rounded-lg">
                <NewspaperIcon className="h-6 w-6 text-white" />
              </div>
              <div>
                <h1 className="text-xl font-bold text-gray-900">FactFeed</h1>
                <p className="text-xs text-gray-500">AI-Powered News Aggregation</p>
              </div>
            </div>
            
            <div className="flex items-center gap-4">
              <button
                onClick={fetchData}
                className="btn-primary"
                disabled={loading}
              >
                <SparklesIcon className="h-4 w-4" />
                {loading ? 'Refreshing...' : 'Refresh'}
              </button>
            </div>
          </div>
        </div>
      </header>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Debug Info */}
        <div className="mb-4 p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
          <h4 className="text-sm font-medium text-yellow-800">Debug Info</h4>
          <p className="text-xs text-yellow-700">
            Loading: {loading.toString()} | Events: {events.length} | Discrepancies: {discrepancies.length}
          </p>
        </div>

        {/* Search and Tabs */}
        <div className="mb-8 space-y-4">
          <div className="relative">
            <MagnifyingGlassIcon className="absolute left-3 top-1/2 transform -translate-y-1/2 h-5 w-5 text-gray-400" />
            <input
              type="text"
              placeholder="Search events or news..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-10 pr-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white shadow-sm"
            />
          </div>

          <div className="flex space-x-1 bg-gray-100 p-1 rounded-lg">
            <button
              onClick={() => setActiveTab('events')}
              className={`flex-1 py-2 px-4 rounded-md text-sm font-medium transition-colors ${
                activeTab === 'events'
                  ? 'bg-white text-blue-600 shadow-sm'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              <div className="flex items-center justify-center gap-2">
                <GlobeAltIcon className="h-4 w-4" />
                News Events ({events.length})
              </div>
            </button>
            <button
              onClick={() => setActiveTab('discrepancies')}
              className={`flex-1 py-2 px-4 rounded-md text-sm font-medium transition-colors ${
                activeTab === 'discrepancies'
                  ? 'bg-white text-blue-600 shadow-sm'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              <div className="flex items-center justify-center gap-2">
                <ExclamationTriangleIcon className="h-4 w-4" />
                Source Discrepancies ({discrepancies.length})
              </div>
            </button>
          </div>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
          </div>
        ) : (
          <>
            {/* Events Tab */}
            {activeTab === 'events' && (
              <div className="space-y-6">
                {filteredEvents.length === 0 ? (
                  <div className="text-center py-12">
                    <NewspaperIcon className="mx-auto h-12 w-12 text-gray-400" />
                    <h3 className="mt-2 text-sm font-medium text-gray-900">No events found</h3>
                    <p className="mt-1 text-sm text-gray-500">
                      {searchTerm ? 'Try adjusting your search terms.' : 'No news events have been processed yet.'}
                    </p>
                  </div>
                ) : (
                  filteredEvents.map((event) => (
                    <div key={event.id} className="card">
                      <div className="flex items-start justify-between mb-4">
                        <div className="flex-1">
                          <h2 className="text-xl font-semibold text-gray-900 mb-2">{event.title}</h2>
                          <div className="flex items-center gap-4 text-sm text-gray-500 mb-3">
                            <div className="flex items-center gap-1">
                              <ClockIcon className="h-4 w-4" />
                              {formatDate(event.eventDate)}
                            </div>
                            <div className="flex items-center gap-1">
                              <DocumentTextIcon className="h-4 w-4" />
                              {event.articleCount} sources
                            </div>
                          </div>
                        </div>
                      </div>

                      <div className="space-y-4">
                        <div>
                          <h3 className="text-sm font-medium text-gray-700 mb-2">AI Summary</h3>
                          <p className="text-gray-900 leading-relaxed">{event.aggregatedSummary}</p>
                        </div>

                        <div>
                          <h3 className="text-sm font-medium text-gray-700 mb-3">Sources</h3>
                          {event.articles && event.articles.length > 0 ? (
                            <div className="grid gap-3 sm:grid-cols-2">
                              {event.articles.map((article) => (
                                <div key={article.id} className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                                  <div className="flex items-start justify-between mb-2">
                                    <span className={`badge ${getSourceColor(article.source)}`}>
                                      {article.source}
                                    </span>
                                    <a
                                      href={article.url}
                                      target="_blank"
                                      rel="noopener noreferrer"
                                      className="text-blue-600 hover:text-blue-700"
                                    >
                                      <EyeIcon className="h-4 w-4" />
                                    </a>
                                  </div>
                                  <h4 className="font-medium text-gray-900 text-sm mb-1">{article.title}</h4>
                                  <p className="text-xs text-gray-600 line-clamp-2">{article.summary}</p>
                                </div>
                              ))}
                            </div>
                          ) : (
                            <div className="bg-gray-50 rounded-lg p-4 border border-gray-200 text-center">
                              <p className="text-sm text-gray-600">
                                {event.articleCount} articles from various sources
                              </p>
                              <p className="text-xs text-gray-500 mt-1">
                                Click to view detailed article breakdown
                              </p>
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  ))
                )}
              </div>
            )}

            {/* Discrepancies Tab */}
            {activeTab === 'discrepancies' && (
              <div className="space-y-6">
                {discrepancies.length === 0 ? (
                  <div className="text-center py-12">
                    <ExclamationTriangleIcon className="mx-auto h-12 w-12 text-gray-400" />
                    <h3 className="mt-2 text-sm font-medium text-gray-900">No discrepancies found</h3>
                    <p className="mt-1 text-sm text-gray-500">All sources are reporting consistent information.</p>
                  </div>
                ) : (
                  discrepancies.map((discrepancy, index) => (
                    <div key={index} className="card border-l-4 border-l-orange-400">
                      <div className="flex items-start gap-3">
                        <div className="p-2 bg-orange-100 rounded-lg">
                          <ExclamationTriangleIcon className="h-5 w-5 text-orange-600" />
                        </div>
                        <div className="flex-1">
                          <h3 className="text-lg font-semibold text-gray-900 mb-2">
                            {discrepancy.title}
                          </h3>
                          <div className="bg-orange-50 border border-orange-200 rounded-lg p-4">
                            <h4 className="text-sm font-medium text-orange-800 mb-2">Source Discrepancies</h4>
                            <p className="text-sm text-orange-700 whitespace-pre-line">
                              {discrepancy.discrepancies}
                            </p>
                          </div>
                          <div className="mt-4">
                            <h4 className="text-sm font-medium text-gray-700 mb-2">Related Sources</h4>
                            <div className="flex flex-wrap gap-2">
                              {discrepancy.articles && discrepancy.articles.length > 0 ? (
                                discrepancy.articles.map((article: Article) => (
                                  <span key={article.id} className={`badge ${getSourceColor(article.source)}`}>
                                    {article.source}
                                  </span>
                                ))
                              ) : (
                                <span className="text-sm text-gray-500">
                                  {discrepancy.articleCount} sources with discrepancies
                                </span>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  ))
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default App;
