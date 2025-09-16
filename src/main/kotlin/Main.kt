package org.example

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import io.github.cdimascio.dotenv.Dotenv
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/*
 A simple assistant that can access the web and help you automate web related tasks, e.g. market or competitor research.
*/

//region JSON instance
@OptIn(ExperimentalSerializationApi::class)
private val json =
    Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
//endregion

//region Web search DTOs
@Serializable
data class WebSearchResult(
    val organic: List<OrganicResult>,
) {
    @Serializable
    data class OrganicResult(
        val link: String,
        val title: String,
        val description: String,
        val rank: Int,
        val globalRank: Int,
    )
}

@Serializable
data class WebPageScrapingResult(
    val body: String, // will be a markdown version of the page
)

@Serializable
data class BrightDataRequest(
    val zone: String,
    val url: String,
    val format: String,
    val dataFormat: String? = null,
)
//endregion

//region Web search tools
class WebSearchTools(
    private val brightDataKey: String,
) : ToolSet {
    //region HTTP client
    private val httpClient =
        HttpClient {
            defaultRequest {
                url("https://api.brightdata.com/request")
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $brightDataKey")
            }

            install(ContentNegotiation) {
                json(json)
            }
        }
    //endregion

    //region Search tool
    @Tool
    @LLMDescription("Search for a query on Google.")
    @Suppress("unused")
    suspend fun search(
        @LLMDescription("The query to search")
        query: String,
    ): WebSearchResult {
        val url =
            URLBuilder("https://www.google.com/search")
                .apply {
                    parameters.append("brd_json", "1")
                    parameters.append("q", query)
                }.buildString()

        val request =
            BrightDataRequest(
                zone = "serp_api1",
                url = url,
                format = "raw",
            )

        val response =
            httpClient
                .post {
                    setBody(request)
                }

        return response.body<WebSearchResult>()
    }
    //endregion

    //region Scrape tool
    @Tool
    @LLMDescription("Scrape a web page for content")
    suspend fun scrape(
        @LLMDescription("The URL to scrape")
        url: String,
    ): WebPageScrapingResult {
        val request =
            BrightDataRequest(
                zone = "web_unlocker1",
                url = url,
                format = "json",
                dataFormat = "markdown",
            )

        val response =
            httpClient
                .post {
                    setBody(request)
                }

        return response.body<WebPageScrapingResult>()
    }
    //endregion
}
//endregion

suspend fun main() {
    // Load from the resources folder, custom filename
    val dotenv = Dotenv.configure()
        .directory("./src/main/resources")
        .filename("API_KEY.env")
        .load()

    val anthropicApiKey =
        dotenv["ANTHROPIC_API_KEY"] ?: System.getenv("ANTHROPIC_API_KEY")
        ?: error("ANTHROPIC_API_KEY is not set in .env or environment")

    val brightDataKey =
        dotenv["BRIGHT_DATA_KEY"] ?: System.getenv("BRIGHT_DATA_KEY")
        ?: error("BRIGHT_DATA_KEY is not set in .env or environment")

    val agentConfig = AIAgentConfig(
        prompt =
            prompt("web_search_prompt") {
                system("You are a helpful assistant that helps users to find information on the internet.")
            },
        model = AnthropicModels.Haiku_3_5,
        maxAgentIterations = 5,
    )

        //region Tool registry
        val webSearchTools = WebSearchTools(brightDataKey)

    val toolRegistry =
        ToolRegistry {
            tools(webSearchTools)
    }


    val agent =
        AIAgent<String,String>(
            promptExecutor = simpleAnthropicExecutor(anthropicApiKey),
            strategy = singleRunStrategy(),
            toolRegistry = toolRegistry,
            agentConfig = agentConfig,
        ) {
            handleEvents {
                onToolCall { ctx ->
                    println("Tool called: tool ${ctx.tool.name}, args ${ctx.toolArgs}")
                }
            }
        }

    val result = agent.run("What is the meaning of life?")
    println(result)
}

