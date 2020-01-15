package org.roc.flink.support.properties;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/**
 * @author roc
 * @since 2020/1/13 13:04
 */
public class ParameterToolPlus {

    private Map<String, Object> defaultProperties;

    private boolean addCommandLineProperties = true;

    private Set<String> additionalProfiles = new HashSet<>();

    private StandardEnvironment standardEnvironment;

    private ParameterToolPlus(String[] args) {
        standardEnvironment = new StandardEnvironment();
        configureEnvironment(standardEnvironment, args);
        new Loader(standardEnvironment, null).load();
    }

    /**
     * 加载所有的配置文件，功效等同于springboot的配置文件加载
     * @return 获取参数工具类
     */
    public static ParameterToolPlus loadProperties() {
        return new ParameterToolPlus(null);
    }

    /**
     * 加载所有的配置文件，功效等同于springboot的配置文件加载
     * @param args 命令行参数
     * @return 获取参数工具类
     */
    public static ParameterToolPlus loadProperties(String[] args) {
        return new ParameterToolPlus(args);
    }

    //---------------------------------------------------------------------
    // Implementation of PropertyResolver interface
    //---------------------------------------------------------------------
    
    public boolean containsProperty(String key) {
        return this.standardEnvironment.containsProperty(key);
    }

    public String getProperty(String key) {
        return this.standardEnvironment.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return this.standardEnvironment.getProperty(key, defaultValue);
    }
    
    public <T> T getProperty(String key, Class<T> targetType) {
        return this.standardEnvironment.getProperty(key, targetType);
    }
    
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        return this.standardEnvironment.getProperty(key, targetType, defaultValue);
    }
    
    public String getRequiredProperty(String key) throws IllegalStateException {
        return this.standardEnvironment.getRequiredProperty(key);
    }

    public <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
        return this.standardEnvironment.getRequiredProperty(key, targetType);
    }

    /**
     * Template method delegating to
     * {@link #configurePropertySources(ConfigurableEnvironment, String[])} and
     * {@link #configureProfiles(ConfigurableEnvironment, String[])} in that order.
     * Override this method for complete control over Environment customization, or one of
     * the above for fine-grained control over property sources or profiles, respectively.
     * @param environment this application's environment
     * @param args arguments passed to the {@code run} method
     * @see #configureProfiles(ConfigurableEnvironment, String[])
     * @see #configurePropertySources(ConfigurableEnvironment, String[])
     */
    protected void configureEnvironment(ConfigurableEnvironment environment,
            String[] args) {
        configurePropertySources(environment, args);
        configureProfiles(environment, args);
    }

    /**
     * Add, remove or re-order any {@link PropertySource}s in this application's
     * environment.
     * @param environment this application's environment
     * @param args arguments passed to the {@code run} method
     * @see #configureEnvironment(ConfigurableEnvironment, String[])
     */
    protected void configurePropertySources(ConfigurableEnvironment environment,
            String[] args) {
        MutablePropertySources sources = environment.getPropertySources();
        if (this.defaultProperties != null && !this.defaultProperties.isEmpty()) {
            sources.addLast(
                    new MapPropertySource("defaultProperties", this.defaultProperties));
        }
        if (this.addCommandLineProperties && args.length > 0) {
            String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
            if (sources.contains(name)) {
                PropertySource<?> source = sources.get(name);
                CompositePropertySource composite = new CompositePropertySource(name);
                composite.addPropertySource(new SimpleCommandLinePropertySource(
                        "springApplicationCommandLineArgs", args));
                composite.addPropertySource(source);
                sources.replace(name, composite);
            }
            else {
                sources.addFirst(new SimpleCommandLinePropertySource(args));
            }
        }
    }

    /**
     * Configure which profiles are active (or active by default) for this application
     * environment. Additional profiles may be activated during configuration file
     * processing via the {@code spring.profiles.active} property.
     * @param environment this application's environment
     * @param args arguments passed to the {@code run} method
     * @see #configureEnvironment(ConfigurableEnvironment, String[])
     * @see org.springframework.boot.context.config.ConfigFileApplicationListener
     */
    protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
        environment.getActiveProfiles(); // ensure they are initialized
        // But these ones should go first (last wins in a property key clash)
        Set<String> profiles = new LinkedHashSet<>(this.additionalProfiles);
        profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
        environment.setActiveProfiles(StringUtils.toStringArray(profiles));
    }

    /**
     * Set additional profile values to use (on top of those set in system or command line
     * properties).
     * @param profiles the additional profiles to set
     */
    public void setAdditionalProfiles(String... profiles) {
        this.additionalProfiles = new LinkedHashSet<>(Arrays.asList(profiles));
    }

    /**
     * Set default environment properties which will be used in addition to those in the
     * existing {@link Environment}.
     * @param defaultProperties the additional properties to set
     */
    public void setDefaultProperties(Map<String, Object> defaultProperties) {
        this.defaultProperties = defaultProperties;
    }

    /**
     * Convenient alternative to {@link #setDefaultProperties(Map)}.
     * @param defaultProperties some {@link Properties}
     */
    public void setDefaultProperties(Properties defaultProperties) {
        this.defaultProperties = new HashMap<>();
        for (Object key : Collections.list(defaultProperties.propertyNames())) {
            this.defaultProperties.put((String) key, defaultProperties.get(key));
        }
    }
}
