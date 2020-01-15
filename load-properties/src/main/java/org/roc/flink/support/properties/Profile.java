package org.roc.flink.support.properties;

import org.springframework.util.Assert;

/**
 * A Spring Profile that can be loaded.
 */
public class Profile {

    private final String name;

    private final boolean defaultProfile;

    Profile(String name) {
        this(name, false);
    }

    Profile(String name, boolean defaultProfile) {
        Assert.notNull(name, "Name must not be null");
        this.name = name;
        this.defaultProfile = defaultProfile;
    }

    public String getName() {
        return this.name;
    }

    public boolean isDefaultProfile() {
        return this.defaultProfile;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        return ((Profile) obj).name.equals(this.name);
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public String toString() {
        return this.name;
    }

}