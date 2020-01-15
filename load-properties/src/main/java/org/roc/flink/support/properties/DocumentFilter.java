package org.roc.flink.support.properties;

/**
 * Filter used to restrict when a {@link Document} is loaded.
 */
@FunctionalInterface
public interface DocumentFilter {

    boolean match(Document document);

}