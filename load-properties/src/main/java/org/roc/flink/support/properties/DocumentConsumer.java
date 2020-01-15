package org.roc.flink.support.properties;

/**
 * Consumer used to handle a loaded {@link Document}.
 */
@FunctionalInterface
public interface DocumentConsumer {

    void accept(Profile profile, Document document);

}