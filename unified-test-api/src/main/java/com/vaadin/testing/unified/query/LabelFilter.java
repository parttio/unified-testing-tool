package com.vaadin.testing.unified.query;

/**
 * Components with a visible label: their {@code label} property ({@code new TextField("Name")}), or the label of the
 * FormLayout item holding them ({@code formLayout.addFormItem(field, "Name")}). The same on every Testing Tool.
 */
public interface LabelFilter<SELF> {
    SELF withLabel(String label);
    SELF withLabelContaining(String part);
}
