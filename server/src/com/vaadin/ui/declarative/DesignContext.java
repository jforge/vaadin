/*
 * Copyright 2000-2014 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.ui.declarative;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import com.vaadin.ui.Component;

/**
 * This class contains contextual information that is collected when a component
 * tree is constructed based on HTML design template. This information includes
 * mappings from local ids, global ids and captions to components , as well as a
 * mapping between prefixes and package names (such as "v" -> "com.vaadin.ui").
 * 
 * @since 7.4
 * @author Vaadin Ltd
 */
public class DesignContext implements Serializable {

    // cache for object instances
    private static Map<Class<?>, Object> instanceCache = Collections
            .synchronizedMap(new HashMap<Class<?>, Object>());

    // The root component of the component hierarchy
    private Component componentRoot = null;
    // Attribute names for global id and caption and the prefix name for a local
    // id
    public static final String ID_ATTRIBUTE = "id";
    public static final String CAPTION_ATTRIBUTE = "caption";
    public static final String LOCAL_ID_ATTRIBUTE = "_id";
    // Mappings from ids to components. Modified when synchronizing from design.
    private Map<String, Component> idToComponent = new HashMap<String, Component>();
    private Map<String, Component> localIdToComponent = new HashMap<String, Component>();
    private Map<String, Component> captionToComponent = new HashMap<String, Component>();
    // Mapping from components to local ids. Accessed when synchronizing to
    // design. Modified when synchronizing from design.
    private Map<Component, String> componentToLocalId = new HashMap<Component, String>();
    private Document doc; // required for calling createElement(String)
    // namespace mappings
    private Map<String, String> packageToPrefix = new HashMap<String, String>();
    private Map<String, String> prefixToPackage = new HashMap<String, String>();
    // prefix names for which no package-mapping element will be created in the
    // html tree (this includes at least "v" which is always taken to refer
    // to "com.vaadin.ui".
    private Map<String, String> defaultPrefixes = new HashMap<String, String>();

    // component creation listeners
    private List<ComponentCreationListener> listeners = new ArrayList<ComponentCreationListener>();

    public DesignContext(Document doc) {
        this.doc = doc;
        // Initialize the mapping between prefixes and package names.
        defaultPrefixes.put("v", "com.vaadin.ui");
        for (String prefix : defaultPrefixes.keySet()) {
            String packageName = defaultPrefixes.get(prefix);
            mapPrefixToPackage(prefix, packageName);
        }
    }

    public DesignContext() {
        this(new Document(""));
    }

    /**
     * Returns a component having the specified local id. If no component is
     * found, returns null.
     * 
     * @param localId
     *            The local id of the component
     * @return a component whose local id equals localId
     */
    public Component getComponentByLocalId(String localId) {
        return localIdToComponent.get(localId);
    }

    /**
     * Returns a component having the specified global id. If no component is
     * found, returns null.
     * 
     * @param globalId
     *            The global id of the component
     * @return a component whose global id equals globalId
     */
    public Component getComponentById(String globalId) {
        return idToComponent.get(globalId);
    }

    /**
     * Returns a component having the specified caption. If no component is
     * found, returns null.
     * 
     * @param caption
     *            The caption of the component
     * @return a component whose caption equals the caption given as a parameter
     */
    public Component getComponentByCaption(String caption) {
        return captionToComponent.get(caption);
    }

    /**
     * Creates a mapping between the given global id and the component. Returns
     * true if globalId was already mapped to some component. Otherwise returns
     * false. Also sets the id of the component to globalId.
     * 
     * If there is a mapping from the component to a global id (gid) different
     * from globalId, the mapping from gid to component is removed.
     * 
     * If the string was mapped to a component c different from the given
     * component, the mapping from c to the string is removed. Similarly, if
     * component was mapped to some string s different from globalId, the
     * mapping from s to component is removed.
     * 
     * @param globalId
     *            The new global id of the component.
     * @param component
     *            The component whose global id is to be set.
     * @return true, if there already was a global id mapping from the string to
     *         some component.
     */
    private boolean mapId(String globalId, Component component) {
        Component oldComponent = idToComponent.get(globalId);
        if (oldComponent != null && !oldComponent.equals(component)) {
            oldComponent.setId(null);
        }
        String oldGID = component.getId();
        if (oldGID != null && !oldGID.equals(globalId)) {
            idToComponent.remove(oldGID);
        }
        component.setId(globalId);
        idToComponent.put(globalId, component);
        return oldComponent != null && !oldComponent.equals(component);
    }

    /**
     * Creates a mapping between the given local id and the component. Returns
     * true if localId was already mapped to some component or if component was
     * mapped to some string. Otherwise returns false.
     * 
     * If the string was mapped to a component c different from the given
     * component, the mapping from c to the string is removed. Similarly, if
     * component was mapped to some string s different from localId, the mapping
     * from s to component is removed.
     * 
     * @param localId
     *            The new local id of the component.
     * @param component
     *            The component whose local id is to be set.
     * @return true, if there already was a local id mapping from the string to
     *         some component or from the component to some string. Otherwise
     *         returns false.
     */
    private boolean mapLocalId(String localId, Component component) {
        return twoWayMap(localId, component, localIdToComponent,
                componentToLocalId);
    }

    /**
     * Creates a mapping between the given caption and the component. Returns
     * true if caption was already mapped to some component.
     * 
     * Note that unlike mapGlobalId, if some component already has the given
     * caption, the caption is not cleared from the component. This allows
     * non-unique captions. However, only one of the components corresponding to
     * a given caption can be found using the map captionToComponent. Hence, any
     * captions that are used to identify an object should be unique.
     * 
     * @param caption
     *            The new caption of the component.
     * @param component
     *            The component whose caption is to be set.
     * @return true, if there already was a caption mapping from the string to
     *         some component.
     */
    private boolean mapCaption(String caption, Component component) {
        return captionToComponent.put(caption, component) != null;
    }

    /**
     * Creates a two-way mapping between key and value, i.e. adds key -> value
     * to keyToValue and value -> key to valueToKey. If key was mapped to a
     * value v different from the given value, the mapping from v to key is
     * removed. Similarly, if value was mapped to some key k different from key,
     * the mapping from k to value is removed.
     * 
     * Returns true if there already was a mapping from key to some value v or
     * if there was a mapping from value to some key k. Otherwise returns false.
     * 
     * @param key
     *            The new key in keyToValue.
     * @param value
     *            The new value in keyToValue.
     * @param keyToValue
     *            A map from keys to values.
     * @param valueToKey
     *            A map from values to keys.
     * @return whether there already was some mapping from key to a value or
     *         from value to a key.
     */
    private <S, T> boolean twoWayMap(S key, T value, Map<S, T> keyToValue,
            Map<T, S> valueToKey) {
        T oldValue = keyToValue.put(key, value);
        if (oldValue != null && !oldValue.equals(value)) {
            valueToKey.remove(oldValue);
        }
        S oldKey = valueToKey.put(value, key);
        if (oldKey != null && !oldKey.equals(key)) {
            keyToValue.remove(oldKey);
        }
        return oldValue != null || oldKey != null;
    }

    /**
     * Creates a two-way mapping between a prefix and a package name. Return
     * true if prefix was already mapped to some package name or packageName to
     * some prefix.
     * 
     * @param prefix
     *            the prefix name without an ending dash (for instance, "v" is
     *            always used for "com.vaadin.ui")
     * @param packageName
     *            the name of the package corresponding to prefix
     * @return whether there was a mapping from prefix to some package name or
     *         from packageName to some prefix.
     */
    private boolean mapPrefixToPackage(String prefix, String packageName) {
        return twoWayMap(prefix, packageName, prefixToPackage, packageToPrefix);
    }

    /**
     * Returns the default instance for the given class. The instance must not
     * be modified by the caller.
     * 
     * @param instanceClass
     * @return the default instance for the given class. The return value must
     *         not be modified by the caller
     */
    public <T> T getDefaultInstance(Class<T> instanceClass) {
        T instance = (T) instanceCache.get(instanceClass);
        if (instance == null) {
            try {
                instance = instanceClass.newInstance();
                instanceCache.put(instanceClass, instance);
            } catch (InstantiationException e) {
                throw new RuntimeException("Could not instantiate "
                        + instanceClass.getName());
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Could not instantiate "
                        + instanceClass.getName());
            }
        }
        return instance;
    }

    /**
     * Get and store the mappings from prefixes to package names from meta tags
     * located under <head> in the html document.
     * 
     */
    public void getPrefixes(Document doc) {
        Element head = doc.head();
        if (head == null) {
            return;
        }
        for (Node child : head.childNodes()) {
            if (child instanceof Element) {
                Element childElement = (Element) child;
                if ("meta".equals(childElement.tagName())) {
                    Attributes attributes = childElement.attributes();
                    if (attributes.hasKey("name")
                            && attributes.hasKey("content")
                            && "package-mapping".equals(attributes.get("name"))) {
                        String contentString = attributes.get("content");
                        String[] parts = contentString.split(":");
                        if (parts.length != 2) {
                            throw new DesignException("The meta tag '"
                                    + child.toString() + "' cannot be parsed.");
                        }
                        String prefixName = parts[0];
                        String packageName = parts[1];
                        twoWayMap(prefixName, packageName, prefixToPackage,
                                packageToPrefix);
                    }
                }
            }
        }
    }

    /**
     * 
     */
    public void storePrefixes(Document doc) {
        Element head = doc.head();
        for (String prefix : prefixToPackage.keySet()) {
            // Only store the prefix-name mapping if it is not a default mapping
            // (such as "v" -> "com.vaadin.ui")
            if (defaultPrefixes.get(prefix) == null) {
                Node newNode = doc.createElement("meta");
                newNode.attr("name", "package-mapping");
                String prefixToPackageName = prefix + ":"
                        + prefixToPackage.get(prefix);
                newNode.attr("content", prefixToPackageName);
                head.appendChild(newNode);

            }
        }
    }

    /**
     * Creates an html tree node corresponding to the given element. Also
     * initializes its attributes by calling synchronizeToDesign. As a result of
     * the synchronizeToDesign() call, this method creates the entire subtree
     * rooted at the returned Node.
     * 
     * @param childComponent
     *            The component with state that is synchronized in to the node
     * @return An html tree node corresponding to the given component. The tag
     *         name of the created node is derived from the class name of
     *         childComponent.
     */
    public Element createNode(Component childComponent) {
        Class<?> componentClass = childComponent.getClass();
        String packageName = componentClass.getPackage().getName();
        String prefix = packageToPrefix.get(packageName);
        if (prefix == null) {
            prefix = packageName.replace('.', '_');
            twoWayMap(prefix, packageName, prefixToPackage, packageToPrefix);
        }
        prefix = prefix + "-";
        String className = classNameToElementName(componentClass
                .getSimpleName());
        Element newElement = doc.createElement(prefix + className);
        childComponent.synchronizeToDesign(newElement, this);
        // Handle the local id. Global id and caption should have been taken
        // care of by synchronizeToDesign.
        String localId = componentToLocalId.get(childComponent);
        if (localId != null) {
            newElement.attr(LOCAL_ID_ATTRIBUTE, localId);
        }
        return newElement;
    }

    /**
     * Creates the name of the html tag corresponding to the given class name.
     * The name is derived by converting each uppercase letter to lowercase and
     * inserting a dash before the letter. No dash is inserted before the first
     * letter of the class name.
     * 
     * @param className
     *            the name of the class without a package name
     * @return the html tag name corresponding to className
     */
    private String classNameToElementName(String className) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < className.length(); i++) {
            Character c = className.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append("-");
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Creates a Component corresponding to the given html node. Also calls
     * synchronizeFromDesign() for the created node, in effect creating the
     * entire component hierarchy rooted at the returned component.
     * 
     * @param componentDesign
     *            The html tree node containing the description of the component
     *            to be created.
     * @return a Component corresponding to componentDesign
     */
    public Component createChild(Element componentDesign) {
        // Create the component.
        Component component = instantiateComponent(componentDesign);
        synchronizeAndRegister(component, componentDesign);
        fireComponentCreatedEvent(componentToLocalId.get(component), component);
        return component;
    }

    /**
     * Calls synchronizeFromDesign() for the given component and passes the
     * given component design as a parameter. This creates the entire component
     * hierarchy rooted at the given component. Also registers the componentid,
     * localId and caption of the given component and all its children to the
     * context
     * 
     * 
     * @param component
     *            The component to be synchronized from design
     * @param componentDesign
     *            The html tree node containing the description of the component
     */
    public void synchronizeAndRegister(Component component,
            Element componentDesign) {
        component.synchronizeFromDesign(componentDesign, this);
        // Get the ids and the caption of the component and store them in the
        // maps of this design context.
        org.jsoup.nodes.Attributes attributes = componentDesign.attributes();
        // global id: only update the mapping, the id has already been set for
        // the component
        String id = component.getId();
        if (id != null && id.length() > 0) {
            boolean mappingExists = mapId(id, component);
            if (mappingExists) {
                throw new DesignException(
                        "The following global id is not unique: " + id);
            }
        }
        // local id: this is not a property of a component, so need to fetch it
        // from the attributes of componentDesign
        if (attributes.hasKey(LOCAL_ID_ATTRIBUTE)) {
            String localId = attributes.get(LOCAL_ID_ATTRIBUTE);
            mapLocalId(localId, component); // two-way map
        }
        // caption: a property of a component, possibly not unique
        String caption = component.getCaption();
        if (caption != null) {
            mapCaption(caption, component);
        }
    }

    /**
     * Creates a Component corresponding to the given node. Does not set the
     * attributes for the created object.
     * 
     * @param node
     *            a node of an html tree
     * @return a Component corresponding to node, with no attributes set.
     */
    private Component instantiateComponent(Node node) {
        // Extract the package and class names.
        String qualifiedClassName = tagNameToClassName(node);
        try {
            Class<? extends Component> componentClass = resolveComponentClass(qualifiedClassName);
            Component newComponent = componentClass.newInstance();
            return newComponent;
        } catch (Exception e) {
            throw new DesignException("No component class could be found for "
                    + node.nodeName() + ".", e);
        }
    }

    /**
     * Returns the qualified class name corresponding to the given html tree
     * node. The class name is extracted from the tag name of node.
     * 
     * @param node
     *            an html tree node
     * @return The qualified class name corresponding to the given node.
     */
    private String tagNameToClassName(Node node) {
        String tagName = node.nodeName();
        if (tagName.equals("v-addon")) {
            return node.attr("class");
        }
        // Otherwise, get the full class name using the prefix to package
        // mapping. Example: "v-vertical-layout" ->
        // "com.vaadin.ui.VerticalLayout"
        String[] parts = tagName.split("-");
        if (parts.length < 2) {
            throw new DesignException("The tagname '" + tagName
                    + "' is invalid: missing prefix.");
        }
        String prefixName = parts[0];
        String packageName = prefixToPackage.get(prefixName);
        if (packageName == null) {
            throw new DesignException("Unknown tag: " + tagName);
        }
        int firstCharacterIndex = prefixName.length() + 1; // +1 is for '-'
        tagName = tagName.substring(firstCharacterIndex,
                firstCharacterIndex + 1).toUpperCase(Locale.ENGLISH)
                + tagName.substring(firstCharacterIndex + 1);
        int i;
        while ((i = tagName.indexOf("-")) != -1) {
            int length = tagName.length();
            if (i != length - 1) {
                tagName = tagName.substring(0, i)
                        + tagName.substring(i + 1, i + 2).toUpperCase(
                                Locale.ENGLISH) + tagName.substring(i + 2);

            } else {
                // Ends with "-", WTF?
                System.out.println("A tag name should not end with '-'.");
            }
        }
        return packageName + "." + tagName;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Component> resolveComponentClass(
            String qualifiedClassName) throws ClassNotFoundException {
        Class<?> componentClass = null;
        componentClass = Class.forName(qualifiedClassName);

        // Check that we're dealing with a Component.
        if (isComponent(componentClass)) {
            return (Class<? extends Component>) componentClass;
        } else {
            throw new IllegalArgumentException(String.format(
                    "Resolved class %s is not a %s.", componentClass.getName(),
                    Component.class.getName()));
        }
    }

    /**
     * Returns {@code true} if the given {@link Class} implements the
     * {@link Component} interface of Vaadin Framework otherwise {@code false}.
     * 
     * @param componentClass
     *            {@link Class} to check against {@link Component} interface.
     * @return {@code true} if the given {@link Class} is a {@link Component},
     *         {@code false} otherwise.
     */
    private static boolean isComponent(Class<?> componentClass) {
        if (componentClass != null) {
            return Component.class.isAssignableFrom(componentClass);
        } else {
            return false;
        }
    }

    /**
     * Returns the root component of a created component hierarchy.
     * 
     * @return
     */
    public Component getComponentRoot() {
        return componentRoot;
    }

    /**
     * Sets the root component of a created component hierarchy.
     */
    public void setComponentRoot(Component componentRoot) {
        this.componentRoot = componentRoot;
    }

    /**
     * Adds a component creation listener. The listener will be notified when
     * components are created while parsing a design template
     * 
     * @param listener
     *            the component creation listener to be added
     */
    public void addComponentCreationListener(ComponentCreationListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a component creation listener.
     * 
     * @param listener
     *            the component creation listener to be removed
     */
    public void removeComponentCreationListener(
            ComponentCreationListener listener) {
        listeners.remove(listener);
    }

    /**
     * Fires component creation event
     * 
     * @param localId
     *            localId of the component
     * @param component
     *            the component that was created
     */
    private void fireComponentCreatedEvent(String localId, Component component) {
        ComponentCreatedEvent event = new ComponentCreatedEvent(localId,
                component);
        for (ComponentCreationListener listener : listeners) {
            listener.componentCreated(event);
        }
    }

    /**
     * Interface to be implemented by component creation listeners
     * 
     * @author Vaadin Ltd
     */
    public interface ComponentCreationListener extends Serializable {

        /**
         * Called when component has been created in the design context
         * 
         * @param event
         *            the component creation event containing information on the
         *            created component
         */
        public void componentCreated(ComponentCreatedEvent event);
    }

    /**
     * Component creation event that is fired when a component is created in the
     * context
     * 
     * @author Vaadin Ltd
     */
    public class ComponentCreatedEvent implements Serializable {
        private String localId;
        private Component component;
        private DesignContext context;

        /**
         * Creates a new instance of ComponentCreatedEvent
         * 
         * @param localId
         *            the local id of the created component
         * @param component
         *            the created component
         */
        private ComponentCreatedEvent(String localId, Component component) {
            this.localId = localId;
            this.component = component;
            context = DesignContext.this;
        }

        /**
         * Returns the local id of the created component or null if not exist
         * 
         * @return the localId
         */
        public String getLocalId() {
            return localId;
        }

        /**
         * Returns the created component
         * 
         * @return the component
         */
        public Component getComponent() {
            return component;
        }
    }

}
