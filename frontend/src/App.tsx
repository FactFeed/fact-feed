import {
	ChevronDownIcon,
	ClockIcon,
	ExclamationTriangleIcon,
	EyeIcon,
	GlobeAltIcon,
	MagnifyingGlassIcon,
	NewspaperIcon
} from "@heroicons/react/24/outline"
import { DocumentTextIcon, SparklesIcon } from "@heroicons/react/24/solid"
import type React from "react"
import {
	useCallback,
	useDeferredValue,
	useEffect,
	useOptimistic,
	useState,
	useTransition
} from "react"

interface Article {
	id: number
	title: string
	source: string
	url: string
	publishedAt: string
	summary: string
	confidenceScore: number
}

interface Event {
	id: number
	title: string
	eventType: string
	aggregatedSummary: string
	discrepancies: string
	confidenceScore: number
	articleCount: number
	eventDate: string
	createdAt: string
	hasDiscrepancies: boolean
	articles: Article[]
}

interface DashboardStats {
	totalEvents: number
	processedEvents: number
	todaysEvents: number
	weeklyEvents: number
	averageConfidenceScore: number
	averageArticleCount: number
	eventsWithDiscrepancies: number
	processingPercentage: number
}

interface ApiResponse {
	events: Event[]
	hasMore: boolean
	nextEventId?: number
	totalCount: number
}

const API_BASE = "http://localhost:8080/api/frontend"

const App: React.FC = () => {
	const [events, setEvents] = useState<Event[]>([])
	const [discrepancies, setDiscrepancies] = useState<Event[]>([])
	const [stats, setStats] = useState<DashboardStats | null>(null)
	const [loading, setLoading] = useState(true)
	const [loadingMore, setLoadingMore] = useState(false)
	const [activeTab, setActiveTab] = useState<"events" | "discrepancies" | "dashboard">("events")
	const [searchTerm, setSearchTerm] = useState("")
	const [hasMore, setHasMore] = useState(true)
	const [nextEventId, setNextEventId] = useState<number>(0)

	// React 19 features
	const [isPending, startTransition] = useTransition()
	const deferredSearchTerm = useDeferredValue(searchTerm)
	const [optimisticEvents, addOptimisticEvent] = useOptimistic(
		events,
		(state, newEvent: Event) => [...state, newEvent]
	)

	const fetchInitialData = useCallback(async () => {
		try {
			setLoading(true)
			console.log("Fetching initial data...")

			// Fetch recent events with articles included
			const eventsResponse = await fetch(`${API_BASE}/events/recent?size=15`, {
				method: "GET",
				headers: { "Content-Type": "application/json" }
			})

			if (!eventsResponse.ok) {
				throw new Error(`HTTP error! status: ${eventsResponse.status}`)
			}

			const eventsData: ApiResponse = await eventsResponse.json()
			setEvents(eventsData.events || [])
			setHasMore(eventsData.hasMore)
			setNextEventId(eventsData.nextEventId || 0)

			// Fetch discrepancies
			const discrepanciesResponse = await fetch(
				`${API_BASE}/events/with-discrepancies?limit=20`
			)
			if (discrepanciesResponse.ok) {
				const discrepanciesData = await discrepanciesResponse.json()
				setDiscrepancies(discrepanciesData || [])
			}

			// Fetch dashboard stats
			const statsResponse = await fetch(`${API_BASE}/dashboard/stats`)
			if (statsResponse.ok) {
				const statsData = await statsResponse.json()
				setStats(statsData)
			}
		} catch (error) {
			console.error("Error fetching initial data:", error)
		} finally {
			setLoading(false)
		}
	}, [])

	const loadMoreEvents = useCallback(async () => {
		if (loadingMore || !hasMore) return

		try {
			setLoadingMore(true)
			const response = await fetch(
				`${API_BASE}/events/recent?size=15&lastEventId=${nextEventId}`,
				{
					method: "GET",
					headers: { "Content-Type": "application/json" }
				}
			)

			if (!response.ok) return

			const data: ApiResponse = await response.json()
			setEvents(prev => [...prev, ...data.events])
			setHasMore(data.hasMore)
			setNextEventId(data.nextEventId || 0)
		} catch (error) {
			console.error("Error loading more events:", error)
		} finally {
			setLoadingMore(false)
		}
	}, [loadingMore, hasMore, nextEventId])

	const searchEvents = useCallback(
		async (query: string) => {
			if (!query.trim()) {
				fetchInitialData()
				return
			}

			try {
				startTransition(() => {
					// Optimistic update - show searching state
					setEvents([])
				})

				const response = await fetch(
					`${API_BASE}/events/search?query=${encodeURIComponent(query)}&size=20`,
					{
						method: "GET",
						headers: { "Content-Type": "application/json" }
					}
				)

				if (!response.ok) return

				const data = await response.json()
				setEvents(data.events || [])
				setHasMore(data.hasMore || false)
			} catch (error) {
				console.error("Error searching events:", error)
			}
		},
		[fetchInitialData]
	)

	// Effect for search with debouncing
	useEffect(() => {
		const timeoutId = setTimeout(() => {
			if (deferredSearchTerm !== searchTerm) return
			searchEvents(deferredSearchTerm)
		}, 300)

		return () => clearTimeout(timeoutId)
	}, [deferredSearchTerm, searchEvents, searchTerm])

	useEffect(() => {
		fetchInitialData()
	}, [fetchInitialData])

	const formatDate = useCallback((dateString: string) => {
		return new Date(dateString).toLocaleDateString("bn-BD", {
			year: "numeric",
			month: "long",
			day: "numeric",
			hour: "2-digit",
			minute: "2-digit"
		})
	}, [])

	const getSourceColor = useCallback((source: string) => {
		const colors = [
			"bg-blue-100 text-blue-800",
			"bg-green-100 text-green-800",
			"bg-purple-100 text-purple-800",
			"bg-orange-100 text-orange-800",
			"bg-pink-100 text-pink-800",
			"bg-indigo-100 text-indigo-800"
		]
		return colors[source.length % colors.length]
	}, [])

	const displayEvents = searchTerm ? events : optimisticEvents

	if (loading) {
		return (
			<div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 flex items-center justify-center">
				<div className="text-center">
					<div className="animate-spin rounded-full h-16 w-16 border-b-2 border-blue-600 mx-auto"></div>
					<p className="mt-4 text-gray-600">Loading FactFeed...</p>
				</div>
			</div>
		)
	}

	return (
		<div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100">
			{/* Header */}
			<header className="bg-white/80 backdrop-blur-sm shadow-sm border-b border-gray-200 sticky top-0 z-50">
				<div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
					<div className="flex items-center justify-between h-16">
						<div className="flex items-center gap-3">
							<div className="p-2 bg-gradient-to-r from-blue-600 to-indigo-600 rounded-xl shadow-lg">
								<NewspaperIcon className="h-6 w-6 text-white" />
							</div>
							<div>
								<h1 className="text-xl font-bold text-gray-900">FactFeed</h1>
								<p className="text-xs text-gray-500">AI-Powered News Aggregation</p>
							</div>
						</div>

						<div className="flex items-center gap-4">
							<button
								onClick={fetchInitialData}
								className="btn-primary flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50"
								disabled={loading || isPending}
							>
								<SparklesIcon className="h-4 w-4" />
								{loading || isPending ? "Refreshing..." : "Refresh"}
							</button>
						</div>
					</div>
				</div>
			</header>

			<div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
				{/* Stats Dashboard */}
				{stats && (
					<div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
						<div className="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
							<div className="text-2xl font-bold text-blue-600">
								{stats.processedEvents}
							</div>
							<div className="text-sm text-gray-600">Processed Events</div>
						</div>
						<div className="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
							<div className="text-2xl font-bold text-green-600">
								{stats.todaysEvents}
							</div>
							<div className="text-sm text-gray-600">Today's Events</div>
						</div>
						<div className="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
							<div className="text-2xl font-bold text-orange-600">
								{stats.eventsWithDiscrepancies}
							</div>
							<div className="text-sm text-gray-600">Discrepancies</div>
						</div>
						<div className="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
							<div className="text-2xl font-bold text-purple-600">
								{stats.averageArticleCount.toFixed(1)}
							</div>
							<div className="text-sm text-gray-600">Avg Articles/Event</div>
						</div>
					</div>
				)}

				{/* Search and Tabs */}
				<div className="mb-8 space-y-4">
					<div className="relative">
						<MagnifyingGlassIcon className="absolute left-4 top-1/2 transform -translate-y-1/2 h-5 w-5 text-gray-400" />
						<input
							type="text"
							placeholder="Search events or news..."
							value={searchTerm}
							onChange={e => setSearchTerm(e.target.value)}
							className="w-full pl-12 pr-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white/80 backdrop-blur-sm shadow-sm"
						/>
						{isPending && (
							<div className="absolute right-4 top-1/2 transform -translate-y-1/2">
								<div className="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-600"></div>
							</div>
						)}
					</div>

					<div className="flex space-x-1 bg-gray-100 p-1 rounded-xl">
						<button
							onClick={() => setActiveTab("events")}
							className={`flex-1 py-3 px-4 rounded-lg text-sm font-medium transition-all ${
								activeTab === "events"
									? "bg-white text-blue-600 shadow-sm transform scale-[1.02]"
									: "text-gray-600 hover:text-gray-900 hover:bg-gray-50"
							}`}
						>
							<div className="flex items-center justify-center gap-2">
								<GlobeAltIcon className="h-4 w-4" />
								News Events ({displayEvents.length})
							</div>
						</button>
						<button
							onClick={() => setActiveTab("discrepancies")}
							className={`flex-1 py-3 px-4 rounded-lg text-sm font-medium transition-all ${
								activeTab === "discrepancies"
									? "bg-white text-blue-600 shadow-sm transform scale-[1.02]"
									: "text-gray-600 hover:text-gray-900 hover:bg-gray-50"
							}`}
						>
							<div className="flex items-center justify-center gap-2">
								<ExclamationTriangleIcon className="h-4 w-4" />
								Discrepancies ({discrepancies.length})
							</div>
						</button>
					</div>
				</div>

				{/* Content */}
				<>
					{/* Events Tab */}
					{activeTab === "events" && (
						<div className="space-y-6">
							{displayEvents.length === 0 ? (
								<div className="text-center py-16">
									<NewspaperIcon className="mx-auto h-16 w-16 text-gray-300" />
									<h3 className="mt-4 text-lg font-medium text-gray-900">
										{searchTerm ? "No events found" : "No events available"}
									</h3>
									<p className="mt-2 text-gray-500">
										{searchTerm
											? "Try adjusting your search terms."
											: "No news events have been processed yet."}
									</p>
								</div>
							) : (
								<>
									{displayEvents.map((event, index) => (
										<div
											key={event.id}
											className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 hover:shadow-md transition-shadow"
										>
											<div className="flex items-start justify-between mb-4">
												<div className="flex-1">
													<h2 className="text-xl font-semibold text-gray-900 mb-3 leading-tight">
														{event.title}
													</h2>
													<div className="flex items-center gap-6 text-sm text-gray-500 mb-4">
														<div className="flex items-center gap-2">
															<ClockIcon className="h-4 w-4" />
															{formatDate(event.eventDate)}
														</div>
														<div className="flex items-center gap-2">
															<DocumentTextIcon className="h-4 w-4" />
															{event.articleCount} sources
														</div>
														<div className="flex items-center gap-2">
															<span
																className={`px-2 py-1 rounded-full text-xs font-medium ${
																	event.eventType === "রাজনীতি"
																		? "bg-red-100 text-red-800"
																		: event.eventType ===
																				"অর্থনীতি"
																			? "bg-green-100 text-green-800"
																			: event.eventType ===
																					"খেলা"
																				? "bg-blue-100 text-blue-800"
																				: "bg-gray-100 text-gray-800"
																}`}
															>
																{event.eventType}
															</span>
														</div>
													</div>
												</div>
											</div>

											<div className="space-y-6">
												{event.aggregatedSummary && (
													<div>
														<h3 className="text-sm font-medium text-gray-700 mb-3 flex items-center gap-2">
															<SparklesIcon className="h-4 w-4 text-blue-500" />
															AI Summary
														</h3>
														<div className="bg-blue-50 rounded-lg p-4 border border-blue-100">
															<p className="text-gray-900 leading-relaxed">
																{event.aggregatedSummary}
															</p>
														</div>
													</div>
												)}

												{event.hasDiscrepancies && event.discrepancies && (
													<div>
														<h3 className="text-sm font-medium text-orange-700 mb-3 flex items-center gap-2">
															<ExclamationTriangleIcon className="h-4 w-4 text-orange-500" />
															Source Discrepancies
														</h3>
														<div className="bg-orange-50 rounded-lg p-4 border border-orange-200">
															<p className="text-orange-800 text-sm leading-relaxed whitespace-pre-line">
																{event.discrepancies}
															</p>
														</div>
													</div>
												)}

												{event.articles && event.articles.length > 0 && (
													<div>
														<h3 className="text-sm font-medium text-gray-700 mb-3 flex items-center gap-2">
															<NewspaperIcon className="h-4 w-4 text-gray-500" />
															Sources ({event.articles.length})
														</h3>
														<div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
															{event.articles.map(article => (
																<div
																	key={article.id}
																	className="bg-gray-50 rounded-lg p-4 border border-gray-200 hover:bg-gray-100 transition-colors"
																>
																	<div className="flex items-start justify-between mb-2">
																		<span
																			className={`px-2 py-1 rounded-md text-xs font-medium ${getSourceColor(article.source)}`}
																		>
																			{article.source}
																		</span>
																		{article.confidenceScore && (
																			<span className="text-xs text-gray-500">
																				{(
																					article.confidenceScore *
																					100
																				).toFixed(0)}
																				%
																			</span>
																		)}
																	</div>
																	<h4 className="font-medium text-gray-900 text-sm mb-2 line-clamp-2">
																		{article.title}
																	</h4>
																	{article.summary && (
																		<p className="text-xs text-gray-600 line-clamp-2">
																			{article.summary}
																		</p>
																	)}
																	<div className="mt-2 text-xs text-gray-500">
																		{formatDate(
																			article.publishedAt
																		)}
																	</div>
																</div>
															))}
														</div>
													</div>
												)}
											</div>
										</div>
									))}

									{/* Load More Button */}
									{hasMore && !searchTerm && (
										<div className="flex justify-center">
											<button
												onClick={loadMoreEvents}
												disabled={loadingMore}
												className="px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center gap-2"
											>
												{loadingMore ? (
													<>
														<div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
														Loading...
													</>
												) : (
													<>
														<ChevronDownIcon className="h-4 w-4" />
														Load More Events
													</>
												)}
											</button>
										</div>
									)}
								</>
							)}
						</div>
					)}

					{/* Discrepancies Tab */}
					{activeTab === "discrepancies" && (
						<div className="space-y-6">
							{discrepancies.length === 0 ? (
								<div className="text-center py-16">
									<ExclamationTriangleIcon className="mx-auto h-16 w-16 text-gray-300" />
									<h3 className="mt-4 text-lg font-medium text-gray-900">
										No discrepancies found
									</h3>
									<p className="mt-2 text-gray-500">
										All sources are reporting consistent information.
									</p>
								</div>
							) : (
								discrepancies.map(discrepancy => (
									<div
										key={discrepancy.id}
										className="bg-white rounded-xl shadow-sm border-l-4 border-l-orange-400 p-6"
									>
										<div className="flex items-start gap-4">
											<div className="p-3 bg-orange-100 rounded-xl">
												<ExclamationTriangleIcon className="h-6 w-6 text-orange-600" />
											</div>
											<div className="flex-1">
												<h3 className="text-lg font-semibold text-gray-900 mb-3">
													{discrepancy.title}
												</h3>
												<div className="bg-orange-50 border border-orange-200 rounded-lg p-4 mb-4">
													<h4 className="text-sm font-medium text-orange-800 mb-2">
														Source Discrepancies
													</h4>
													<p className="text-sm text-orange-700 whitespace-pre-line leading-relaxed">
														{discrepancy.discrepancies}
													</p>
												</div>
												{discrepancy.articles &&
													discrepancy.articles.length > 0 && (
														<div>
															<h4 className="text-sm font-medium text-gray-700 mb-3">
																Related Sources (
																{discrepancy.articles.length})
															</h4>
															<div className="flex flex-wrap gap-2">
																{discrepancy.articles.map(
																	(article: Article) => (
																		<span
																			key={article.id}
																			className={`px-3 py-1 rounded-full text-xs font-medium ${getSourceColor(article.source)}`}
																		>
																			{article.source}
																		</span>
																	)
																)}
															</div>
														</div>
													)}
											</div>
										</div>
									</div>
								))
							)}
						</div>
					)}
				</>
			</div>
		</div>
	)
}

export default App
