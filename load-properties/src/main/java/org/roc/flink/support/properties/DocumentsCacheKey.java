package org.roc.flink.support.properties;

import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.io.Resource;

/**
 * Cache key used to save loading the same document multiple times.
 */
public class DocumentsCacheKey {

    private final PropertySourceLoader loader;

    private final Resource resource;

    DocumentsCacheKey(PropertySourceLoader loader, Resource resource) {
        this.loader = loader;
        this.resource = resource;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DocumentsCacheKey other = (DocumentsCacheKey) obj;
        return this.loader.equals(other.loader)
                && this.resource.equals(other.resource);
    }

    @Override
    public int hashCode() {
        return this.loader.hashCode() * 31 + this.resource.hashCode();
    }

}