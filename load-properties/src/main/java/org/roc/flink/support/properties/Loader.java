package org.roc.flink.support.properties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * Loads candidate property sources and configures the active profiles.
 */
public class Loader {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Loader.class);
    
    /**
     * The "active profiles" property name.
     */
    public static final String ACTIVE_PROFILES_PROPERTY = "spring.profiles.active";
    /**
     * The "includes profiles" property name.
     */
    public static final String INCLUDE_PROFILES_PROPERTY = "spring.profiles.include";
    /**
     * The "config name" property name.
     */
    public static final String CONFIG_NAME_PROPERTY = "spring.config.name";
    /**
     * The "config location" property name.
     */
    public static final String CONFIG_LOCATION_PROPERTY = "spring.config.location";
    /**
     * The "config additional location" property name.
     */
    public static final String CONFIG_ADDITIONAL_LOCATION_PROPERTY = "spring.config.additional-location";
    /**
     * Name of the application configuration {@link PropertySource}.
     */
    @Deprecated
    public static final String APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME = "applicationConfigurationProperties";
    private static final String DEFAULT_PROPERTIES = "defaultProperties";
    // Note the order is from least to most specific (last one wins)
    private static final String DEFAULT_SEARCH_LOCATIONS = "classpath:/,classpath:/config/,file:./,file:./config/";
    private static final String DEFAULT_NAMES = "application";
    private static final Set<String> NO_SEARCH_NAMES = Collections.singleton(null);
    private final ConfigurableEnvironment environment;
    private final ResourceLoader resourceLoader;
    private final List<PropertySourceLoader> propertySourceLoaders;
    private String searchLocations;
    private String names;

    private Deque<Profile> profiles;

    private List<Profile> processedProfiles;

    private boolean activatedProfiles;

    private Map<Profile, MutablePropertySources> loaded;

    private Map<DocumentsCacheKey, List<Document>> loadDocumentsCache = new HashMap<>();

    public Loader(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
        this.environment = environment;
        this.resourceLoader = (resourceLoader != null) ? resourceLoader
                : new DefaultResourceLoader();
        this.propertySourceLoaders = SpringFactoriesLoader.loadFactories(
                PropertySourceLoader.class, getClass().getClassLoader());
    }

    public void load() {
        this.profiles = new LinkedList<>();
        this.processedProfiles = new LinkedList<>();
        this.activatedProfiles = false;
        this.loaded = new LinkedHashMap<>();
        initializeProfiles();
        while (!this.profiles.isEmpty()) {
            Profile profile = this.profiles.poll();
            if (profile != null && !profile.isDefaultProfile()) {
                addProfileToEnvironment(profile.getName());
            }
            load(profile, this::getPositiveProfileFilter,
                    addToLoaded(MutablePropertySources::addLast, false));
            this.processedProfiles.add(profile);
        }
        resetEnvironmentProfiles(this.processedProfiles);
        load(null, this::getNegativeProfileFilter,
                addToLoaded(MutablePropertySources::addFirst, true));
        addLoadedPropertySources();
    }

    /**
     * Initialize profile information from both the {@link Environment} active profiles and any {@code
     * spring.profiles.active}/{@code spring.profiles.include} properties that are already set.
     */
    private void initializeProfiles() {
        // The default profile for these purposes is represented as null. We add it
        // first so that it is processed first and has lowest priority.
        this.profiles.add(null);
        Set<Profile> activatedViaProperty = getProfilesActivatedViaProperty();
        this.profiles.addAll(getOtherActiveProfiles(activatedViaProperty));
        // Any pre-existing active profiles set via property sources (e.g.
        // System properties) take precedence over those added in config files.
        addActiveProfiles(activatedViaProperty);
        if (this.profiles.size() == 1) { // only has null profile
            for (String defaultProfileName : this.environment.getDefaultProfiles()) {
                Profile defaultProfile = new Profile(defaultProfileName, true);
                this.profiles.add(defaultProfile);
            }
        }
    }

    private Set<Profile> getProfilesActivatedViaProperty() {
        if (!this.environment.containsProperty(ACTIVE_PROFILES_PROPERTY)
                && !this.environment.containsProperty(INCLUDE_PROFILES_PROPERTY)) {
            return Collections.emptySet();
        }
        Binder binder = Binder.get(this.environment);
        Set<Profile> activeProfiles = new LinkedHashSet<>();
        activeProfiles.addAll(getProfiles(binder, INCLUDE_PROFILES_PROPERTY));
        activeProfiles.addAll(getProfiles(binder, ACTIVE_PROFILES_PROPERTY));
        return activeProfiles;
    }

    private List<Profile> getOtherActiveProfiles(Set<Profile> activatedViaProperty) {
        return Arrays.stream(this.environment.getActiveProfiles()).map(Profile::new)
                .filter((profile) -> !activatedViaProperty.contains(profile))
                .collect(Collectors.toList());
    }

    private void addActiveProfiles(Set<Profile> profiles) {
        if (profiles.isEmpty()) {
            return;
        }
        if (this.activatedProfiles) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Profiles already activated, '" + profiles
                        + "' will not be applied");
            }
            return;
        }
        this.profiles.addAll(profiles);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Activated activeProfiles "
                    + StringUtils.collectionToCommaDelimitedString(profiles));
        }
        this.activatedProfiles = true;
        removeUnprocessedDefaultProfiles();
    }

    private void removeUnprocessedDefaultProfiles() {
        this.profiles.removeIf(
                (profile) -> (profile != null && profile.isDefaultProfile()));
    }

    private DocumentFilter getPositiveProfileFilter(Profile profile) {
        return (Document document) -> {
            if (profile == null) {
                return ObjectUtils.isEmpty(document.getProfiles());
            }
            return ObjectUtils.containsElement(document.getProfiles(),
                    profile.getName())
                    && this.environment.acceptsProfiles(document.getProfiles());
        };
    }

    private DocumentFilter getNegativeProfileFilter(Profile profile) {
        return (Document document) -> (profile == null
                && !ObjectUtils.isEmpty(document.getProfiles())
                && this.environment.acceptsProfiles(document.getProfiles()));
    }

    private DocumentConsumer addToLoaded(
            BiConsumer<MutablePropertySources, PropertySource<?>> addMethod,
            boolean checkForExisting) {
        return (profile, document) -> {
            if (checkForExisting) {
                for (MutablePropertySources merged : this.loaded.values()) {
                    if (merged.contains(document.getPropertySource().getName())) {
                        return;
                    }
                }
            }
            MutablePropertySources merged = this.loaded.computeIfAbsent(profile,
                    (k) -> new MutablePropertySources());
            addMethod.accept(merged, document.getPropertySource());
        };
    }

    private void load(Profile profile, DocumentFilterFactory filterFactory,
            DocumentConsumer consumer) {
        getSearchLocations().forEach((location) -> {
            boolean isFolder = location.endsWith("/");
            Set<String> names = isFolder ? getSearchNames() : NO_SEARCH_NAMES;
            names.forEach(
                    (name) -> load(location, name, profile, filterFactory, consumer));
        });
    }

    private void load(String location, String name, Profile profile,
            DocumentFilterFactory filterFactory, DocumentConsumer consumer) {
        if (!StringUtils.hasText(name)) {
            for (PropertySourceLoader loader : this.propertySourceLoaders) {
                if (canLoadFileExtension(loader, location)) {
                    load(loader, location, profile,
                            filterFactory.getDocumentFilter(profile), consumer);
                    return;
                }
            }
        }
        Set<String> processed = new HashSet<>();
        for (PropertySourceLoader loader : this.propertySourceLoaders) {
            for (String fileExtension : loader.getFileExtensions()) {
                if (processed.add(fileExtension)) {
                    loadForFileExtension(loader, location + name, "." + fileExtension,
                            profile, filterFactory, consumer);
                }
            }
        }
    }

    private boolean canLoadFileExtension(PropertySourceLoader loader, String name) {
        return Arrays.stream(loader.getFileExtensions())
                .anyMatch((fileExtension) -> StringUtils.endsWithIgnoreCase(name,
                        fileExtension));
    }

    private void loadForFileExtension(PropertySourceLoader loader, String prefix,
            String fileExtension, Profile profile,
            DocumentFilterFactory filterFactory, DocumentConsumer consumer) {
        DocumentFilter defaultFilter = filterFactory.getDocumentFilter(null);
        DocumentFilter profileFilter = filterFactory.getDocumentFilter(profile);
        if (profile != null) {
            // Try profile-specific file & profile section in profile file (gh-340)
            String profileSpecificFile = prefix + "-" + profile + fileExtension;
            load(loader, profileSpecificFile, profile, defaultFilter, consumer);
            load(loader, profileSpecificFile, profile, profileFilter, consumer);
            // Try profile specific sections in files we've already processed
            for (Profile processedProfile : this.processedProfiles) {
                if (processedProfile != null) {
                    String previouslyLoaded = prefix + "-" + processedProfile
                            + fileExtension;
                    load(loader, previouslyLoaded, profile, profileFilter, consumer);
                }
            }
        }
        // Also try the profile-specific section (if any) of the normal file
        load(loader, prefix + fileExtension, profile, profileFilter, consumer);
    }

    private void load(PropertySourceLoader loader, String location, Profile profile,
            DocumentFilter filter, DocumentConsumer consumer) {
        try {
            Resource resource = this.resourceLoader.getResource(location);
            if (resource == null || !resource.exists()) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Skipped missing config "
                            + getDescription(location, resource, profile));
                }
                return;
            }
            if (!StringUtils.hasText(
                    StringUtils.getFilenameExtension(resource.getFilename()))) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Skipped empty config extension "
                            + getDescription(location, resource, profile));
                }
                return;
            }
            String name = "applicationConfig: [" + location + "]";
            List<Document> documents = loadDocuments(loader, name, resource);
            if (CollectionUtils.isEmpty(documents)) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Skipped unloaded config "
                            + getDescription(location, resource, profile));
                }
                return;
            }
            List<Document> loaded = new ArrayList<>();
            for (Document document : documents) {
                if (filter.match(document)) {
                    addActiveProfiles(document.getActiveProfiles());
                    addIncludedProfiles(document.getIncludeProfiles());
                    loaded.add(document);
                }
            }
            Collections.reverse(loaded);
            if (!loaded.isEmpty()) {
                loaded.forEach((document) -> consumer.accept(profile, document));
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Loaded config file "
                            + getDescription(location, resource, profile));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load property "
                    + "source from location '" + location + "'", ex);
        }
    }

    private void addIncludedProfiles(Set<Profile> includeProfiles) {
        LinkedList<Profile> existingProfiles = new LinkedList<>(this.profiles);
        this.profiles.clear();
        this.profiles.addAll(includeProfiles);
        this.profiles.removeAll(this.processedProfiles);
        this.profiles.addAll(existingProfiles);
    }

    private List<Document> loadDocuments(PropertySourceLoader loader, String name,
            Resource resource) throws IOException {
        DocumentsCacheKey cacheKey = new DocumentsCacheKey(loader, resource);
        List<Document> documents = this.loadDocumentsCache.get(cacheKey);
        if (documents == null) {
            List<PropertySource<?>> loaded = loader.load(name, resource);
            documents = asDocuments(loaded);
            this.loadDocumentsCache.put(cacheKey, documents);
        }
        return documents;
    }

    private List<Document> asDocuments(List<PropertySource<?>> loaded) {
        if (loaded == null) {
            return Collections.emptyList();
        }
        return loaded.stream().map((propertySource) -> {
            Binder binder = new Binder(
                    ConfigurationPropertySources.from(propertySource),
                    new PropertySourcesPlaceholdersResolver(this.environment));
            return new Document(propertySource,
                    binder.bind("spring.profiles", Bindable.of(String[].class))
                            .orElse(null),
                    getProfiles(binder, ACTIVE_PROFILES_PROPERTY),
                    getProfiles(binder, INCLUDE_PROFILES_PROPERTY));
        }).collect(Collectors.toList());
    }

    private String getDescription(String location, Resource resource,
            Profile profile) {
        String description = getDescription(location, resource);
        return (profile != null) ? description + " for profile " + profile
                : description;
    }

    private String getDescription(String location, Resource resource) {
        try {
            if (resource != null) {
                String uri = resource.getURI().toASCIIString();
                return String.format("'%s' (%s)", uri, location);
            }
        } catch (IOException ex) {
        }
        return String.format("'%s'", location);
    }

    private Set<Profile> getProfiles(Binder binder, String name) {
        return binder.bind(name, String[].class).map(this::asProfileSet)
                .orElse(Collections.emptySet());
    }

    private Set<Profile> asProfileSet(String[] profileNames) {
        List<Profile> profiles = new ArrayList<>();
        for (String profileName : profileNames) {
            profiles.add(new Profile(profileName));
        }
        return new LinkedHashSet<>(profiles);
    }

    private void addProfileToEnvironment(String profile) {
        for (String activeProfile : this.environment.getActiveProfiles()) {
            if (activeProfile.equals(profile)) {
                return;
            }
        }
        this.environment.addActiveProfile(profile);
    }

    private Set<String> getSearchLocations() {
        if (this.environment.containsProperty(CONFIG_LOCATION_PROPERTY)) {
            return getSearchLocations(CONFIG_LOCATION_PROPERTY);
        }
        Set<String> locations = getSearchLocations(
                CONFIG_ADDITIONAL_LOCATION_PROPERTY);
        locations.addAll(
                asResolvedSet(this.searchLocations,
                        DEFAULT_SEARCH_LOCATIONS));
        return locations;
    }

    private Set<String> getSearchLocations(String propertyName) {
        Set<String> locations = new LinkedHashSet<>();
        if (this.environment.containsProperty(propertyName)) {
            for (String path : asResolvedSet(
                    this.environment.getProperty(propertyName), null)) {
                if (!path.contains("$")) {
                    path = StringUtils.cleanPath(path);
                    if (!ResourceUtils.isUrl(path)) {
                        path = ResourceUtils.FILE_URL_PREFIX + path;
                    }
                }
                locations.add(path);
            }
        }
        return locations;
    }

    private Set<String> getSearchNames() {
        if (this.environment.containsProperty(CONFIG_NAME_PROPERTY)) {
            String property = this.environment.getProperty(CONFIG_NAME_PROPERTY);
            return asResolvedSet(property, null);
        }
        return asResolvedSet(this.names, DEFAULT_NAMES);
    }

    private Set<String> asResolvedSet(String value, String fallback) {
        List<String> list = Arrays.asList(StringUtils.trimArrayElements(
                StringUtils.commaDelimitedListToStringArray((value != null)
                        ? this.environment.resolvePlaceholders(value) : fallback)));
        Collections.reverse(list);
        return new LinkedHashSet<>(list);
    }

    /**
     * This ensures that the order of active profiles in the {@link Environment} matches the order in which the profiles
     * were processed.
     *
     * @param processedProfiles the processed profiles
     */
    private void resetEnvironmentProfiles(List<Profile> processedProfiles) {
        String[] names = processedProfiles.stream()
                .filter((profile) -> profile != null && !profile.isDefaultProfile())
                .map(Profile::getName).toArray(String[]::new);
        this.environment.setActiveProfiles(names);
    }

    private void addLoadedPropertySources() {
        MutablePropertySources destination = this.environment.getPropertySources();
        List<MutablePropertySources> loaded = new ArrayList<>(this.loaded.values());
        Collections.reverse(loaded);
        String lastAdded = null;
        Set<String> added = new HashSet<>();
        for (MutablePropertySources sources : loaded) {
            for (PropertySource<?> source : sources) {
                if (added.add(source.getName())) {
                    addLoadedPropertySource(destination, lastAdded, source);
                    lastAdded = source.getName();
                }
            }
        }
    }

    private void addLoadedPropertySource(MutablePropertySources destination,
            String lastAdded, PropertySource<?> source) {
        if (lastAdded == null) {
            if (destination.contains(DEFAULT_PROPERTIES)) {
                destination.addBefore(DEFAULT_PROPERTIES, source);
            } else {
                destination.addLast(source);
            }
        } else {
            destination.addAfter(lastAdded, source);
        }
    }

    /**
     * Set the search locations that will be considered as a comma-separated list. Each
     * search location should be a directory path (ending in "/") and it will be prefixed
     * by the file names constructed from {@link #setSearchNames(String) search names} and
     * profiles (if any) plus file extensions supported by the properties loaders.
     * Locations are considered in the order specified, with later items taking precedence
     * (like a map merge).
     * @param locations the search locations
     */
    public void setSearchLocations(String locations) {
        Assert.hasLength(locations, "Locations must not be empty");
        this.searchLocations = locations;
    }


    /**
     * Sets the names of the files that should be loaded (excluding file extension) as a
     * comma-separated list.
     * @param names the names to load
     */
    public void setSearchNames(String names) {
        Assert.hasLength(names, "Names must not be empty");
        this.names = names;
    }

}