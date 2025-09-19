package com.factfeed.backend.scraper.config;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@Slf4j
public class WebDriverConfig {

    @Value("${scraper.webdriver.type:chrome}")
    private String webDriverType;

    @Value("${scraper.webdriver.headless:true}")
    private boolean headless;

    @Value("${scraper.webdriver.timeout:30}")
    private int timeoutSeconds;

    @Value("${scraper.webdriver.window.width:1920}")
    private int windowWidth;

    @Value("${scraper.webdriver.window.height:1080}")
    private int windowHeight;

    @Bean
    @Scope("prototype")
    public WebDriver webDriver() {
        log.info("Creating WebDriver instance: type={}, headless={}", webDriverType, headless);

        WebDriver driver = switch (webDriverType.toLowerCase()) {
            case "firefox" -> createFirefoxDriver();
            default -> createChromeDriver();
        };

        // Configure timeouts
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(timeoutSeconds));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        // Set window size
        driver.manage().window().setSize(new org.openqa.selenium.Dimension(windowWidth, windowHeight));

        log.info("WebDriver created successfully");
        return driver;
    }

    private WebDriver createChromeDriver() {
        ChromeOptions options = new ChromeOptions();

        if (headless) {
            options.addArguments("--headless");
        }

        // Security and performance options
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-images");

        // User agent
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36");

        // Memory optimization
        options.addArguments("--memory-pressure-off");
        options.addArguments("--max_old_space_size=4096");

        return new ChromeDriver(options);
    }

    private WebDriver createFirefoxDriver() {
        FirefoxOptions options = new FirefoxOptions();

        if (headless) {
            options.addArguments("--headless");
        }

        // Performance options
        options.addPreference("javascript.enabled", false); // We'll re-enable when needed
        options.addPreference("permissions.default.image", 2); // Block images
        options.addPreference("dom.ipc.plugins.enabled.libflashplayer.so", false);

        return new FirefoxDriver(options);
    }
}