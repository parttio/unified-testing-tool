package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.TextFilter;

@UnifiedComponent(Notification.class)
public interface UnifiedNotification extends UnifiedWrapper,
        CommonFilters<UnifiedNotification>, TextFilter<UnifiedNotification> {

    default String getText() { return element().getText(); }
    default boolean isOpen() { return Boolean.TRUE.equals(element().getPropertyBoolean("opened")); }
}
