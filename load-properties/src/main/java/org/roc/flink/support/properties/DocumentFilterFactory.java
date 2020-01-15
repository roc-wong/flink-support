package org.roc.flink.support.properties;

/**
 * Factory used to create a {@link DocumentFilter}.
 */
@FunctionalInterface
public interface DocumentFilterFactory {

    /**
     * Create a filter for the given profile.
     *
     * @param profile the profile or {@code null}
     * @return the filter
     */
    DocumentFilter getDocumentFilter(Profile profile);

}