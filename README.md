# AIAgentProtoTyp

AIAgentProtoTyp is a small Kotlin project that demonstrates how to build an AI assistant that can search the web and scrape pages to answer user questions. It uses:

- Koog Agents (ai.koog) to define and run the AI agent
- Anthropic Claude (Sonnet 4) as the LLM
- Bright Data’s scraping API for Google Search results and page scraping
- Ktor HTTP client for API calls
- dotenv to load API keys from a local env file

This README is written for junior developers who are new to Kotlin/Gradle and AI agent frameworks.


## What the project does (high level)

- Accepts a user question.
- Calls a Google Search tool (via Bright Data) to find relevant links.
- Optionally scrapes a target page and converts it to markdown (via Bright Data) so the agent can read it.
- Uses an LLM (Anthropic) to synthesize an answer from what it found.

All of this is coordinated by a Koog AIAgent which knows about the available tools and when to call them.


## Project layout

- src/main/kotlin/Main.kt — the entire agent setup and the two tools (search and scrape)
- src/main/resources/API_KEY.env — place your API keys here (not checked into VCS typically)
- src/main/resources/logback.xml — logging configuration for Ktor/Koog/etc.
- build.gradle.kts — Gradle build configuration
- gradle.properties, settings.gradle.kts — Gradle plumbing


## Prerequisites

- Java 21+ (the Gradle config targets JVM toolchain 24, but having a recent JDK like 21 or 23 is fine when using IntelliJ’s Gradle-managed toolchain)
- IntelliJ IDEA (recommended) with Kotlin plugin
- Accounts and API keys for:
  - Anthropic (for Claude)
  - Bright Data (for search and scraping). Your Bright Data configuration must have zones that match the ones used in code (see below).


## Configuration: API keys

Main.kt loads keys from src/main/resources/API_KEY.env (using java-dotenv). It also falls back to OS environment variables if the .env file is not present.

Add a file at src/main/resources/API_KEY.env with this content:

```
ANTHROPIC_API_KEY=your_anthropic_key_here
BRIGHT_DATA_KEY=your_bright_data_key_here
```

Alternatively, set them as system environment variables:

- ANTHROPIC_API_KEY
- BRIGHT_DATA_KEY

If either key is missing at runtime, the app will error with a clear message.


## How it works (step by step)

Everything happens in Main.kt:

1) JSON setup
   - A kotlinx.serialization Json instance is configured to read/write snake_case JSON and ignore unknown fields. This is used by Ktor’s ContentNegotiation plugin.

2) Data classes (DTOs)
   - WebSearchResult: shape of the Google results we get back from Bright Data (organic results list).
   - WebPageScrapingResult: body field holds the page content (returned as markdown).
   - BrightDataRequest: generic request we send to Bright Data.

3) Tools (WebSearchTools)
   - search(query: String): Calls Bright Data’s request API with a Google search URL. Expects a JSON payload shaped like WebSearchResult.
   - scrape(url: String): Calls Bright Data’s request API to fetch a web page as markdown, then trims very long content to avoid sending too much to the LLM.
   - Both tools are annotated with @Tool and @LLMDescription so the agent can discover and call them.

   Bright Data specifics used by this project:
   - Host: https://api.brightdata.com/request (Authorization: Bearer <BRIGHT_DATA_KEY>)
   - Search uses zone "serp_api1" and format "raw" for Google SERP JSON.
   - Scrape uses zone "web_unlocker1" with format "json" and dataFormat "markdown".
   Your Bright Data account must have zones with these names and capabilities. If your zones are named differently, edit Main.kt (BrightDataRequest.zone fields) to use your zone names.

4) Agent setup
   - AIAgentConfig defines:
     - system prompt: tells the agent it’s a helpful assistant for internet research
     - model: AnthropicModels.Sonnet_4
     - maxAgentIterations: 30 (how many tool/LLM cycles are allowed)
   - Tools are registered via ToolRegistry { tools(webSearchTools) } so the agent can call search/scrape.
   - The Anthropic executor is constructed with your ANTHROPIC_API_KEY.
   - handleEvents logs when a tool is called (useful for debugging).

5) Running one task
   - The code calls agent.run("Who is the president of the United States as of today?, tell me as well from which website you scraped the information").
   - The agent will decide to call search and possibly scrape, then produce an answer. The final answer is printed to the console.


## Running the project

The simplest way (recommended for juniors) is using IntelliJ IDEA:

1) Open the project directory in IntelliJ.
2) Let Gradle sync finish (it will download dependencies like ai.koog, ktor, etc.).
3) Open src/main/kotlin/Main.kt.
4) Click the gutter run icon next to the main function (suspend fun main()) or create a Run Configuration for Kotlin.
5) Make sure your API keys are set (via API_KEY.env or OS env vars) before running.

Notes about Gradle tasks:
- This project does not currently apply the application plugin, so there is no default gradlew run task. Running from IntelliJ is easiest.
- If you prefer CLI runs, you could add the Kotlin application plugin and a mainClass entry later.


## Logging

Logging is configured by src/main/resources/logback.xml. If you need more/less logging, edit that file. Ktor and Koog can be verbose during development; tune levels to your needs.


## Customizing the agent

- Change the model: In Main.kt, set a different Anthropic model if desired (e.g., Haiku) via AnthropicModels.
- Adjust the system prompt: Edit the prompt("web_search_prompt") block to change the assistant’s behavior.
- Add or modify tools:
  - Add new @Tool functions to WebSearchTools or create another ToolSet class.
  - Register them in ToolRegistry { tools(yourTools) }.
- Change Bright Data zones: If your account uses different zone names (common), update the zone fields in BrightDataRequest for search/scrape.
- Control content length: scrape() trims large bodies to ~50k characters to avoid overwhelming LLM context. Adjust maxChars as needed.


## Common errors and troubleshooting

- Error: "ANTHROPIC_API_KEY is not set in .env or environment"
  - Solution: Add it to src/main/resources/API_KEY.env or set the OS environment variable.

- Error: "BRIGHT_DATA_KEY is not set in .env or environment"
  - Solution: Same approach as above.

- Bright Data 401/403 errors
  - Check that your BRIGHT_DATA_KEY is valid and the Authorization header uses Bearer <key>.
  - Ensure the zone names (serp_api1, web_unlocker1) exist in your Bright Data account and have the right permissions.

- Unexpected JSON shape when parsing search results
  - Bright Data response fields may differ by zone/config. Update WebSearchResult to match what your zone returns, or change the zone/format to one that matches the current DTO.

- The agent never calls tools
  - Ensure tools are registered in ToolRegistry.
  - Ensure the system prompt encourages web research.
  - Increase maxAgentIterations if it stops too early.

- Rate limits / context limits
  - scrape() already truncates content. You can further limit or chunk content.


## Security notes

- Never commit real API keys. API_KEY.env should be in .gitignore (this project includes a .gitignore at the root).
- Consider using environment variables on CI/servers instead of files.
- Be careful with the URLs you scrape and comply with target sites’ terms of service.


## Where to look in the code

- The entry point and all logic live in: src/main/kotlin/Main.kt
- Key parts to read first:
  - WebSearchTools class (search and scrape)
  - AIAgent setup in main() (AIAgentConfig, tool registry, event handlers)


## Next steps you can try

- Change the user query passed to agent.run().
- Add a tool to fetch a specific API (e.g., Wikipedia or a JSON API) and teach the agent to use it.
- Switch the model to a cheaper/faster one for experimentation.
- Add unit tests around your tools, mocking Ktor HttpClient responses.


## License

This prototype is for educational/demo purposes. Add a license here if you plan to distribute it.