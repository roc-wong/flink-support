package org.roc.flink.support.properties;

import java.util.Set;
import org.springframework.core.env.PropertySource;

/**
 * A single document loaded by a {@link PropertySourceLoader}.
 */
public class Document {

    private final PropertySource<?> propertySource;
    private final Set<Profile> activeProfiles;
    private final Set<Profile> includeProfiles;
    private String[] profiles;

    Document(PropertySource<?> propertySource, String[] profiles,
            Set<Profile> activeProfiles, Set<Profile> includeProfiles) {
        this.propertySource = propertySource;
        this.profiles = profiles;
        this.activeProfiles = activeProfiles;
        this.includeProfiles = includeProfiles;
    }

    public PropertySource<?> getPropertySource() {
        return this.propertySource;
    }

    public String[] getProfiles() {
        return this.profiles;
    }

    public Set<Profile> getActiveProfiles() {
        return this.activeProfiles;
    }

    public Set<Profile> getIncludeProfiles() {
        return this.includeProfiles;
    }

    @Override
    public String toString() {
        return this.propertySource.toString();
    }

}