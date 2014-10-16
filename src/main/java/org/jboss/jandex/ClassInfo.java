/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.jandex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents a class entry in an index. A ClassInfo is only a partial view of a
 * Java class, it is not intended as a complete replacement for Java reflection.
 * Only the methods and fields which are references by an annotation are stored.
 *
 * <p>Global information including the parent class, implemented methodParameters, and
 * access flags are also provided since this information is often necessary.
 *
 * <p>Note that a parent class and interface may exist outside of the scope of the
 * index (e.g. classes in a different jar) so the references are stored as names
 * instead of direct references. It is expected that multiple indexes may need
 * to be queried to assemble a full hierarchy in a complex multi-jar environment
 * (e.g. an application server).
 *
 * <p><b>Thread-Safety</b></p>
 * This class is immutable and can be shared between threads without safe publication.
 *
 * @author Jason T. Greene
 *
 */
public final class ClassInfo implements AnnotationTarget {

    private final DotName name;
    private final short flags;
    private final DotName superName;
    private final List<DotName> interfaces;
    private final Map<DotName, List<AnnotationInstance>> annotations;

    // Not final to allow lazy initialization, immutable once published
    private Type[] interfaceTypes;
    private Type superClassType;
    private Type[] typeParameters;
    private MethodInternal[] methods;
    private FieldInternal[] fields;
    private boolean hasNoArgsConstructor;
    private NestingInfo nestingInfo;

    public enum NestingType {TOP_LEVEL, INNER, LOCAL, ANONYMOUS}

    private static final class NestingInfo {
        private DotName enclosingClass;
        private String simpleName;
        private EnclosingMethodInfo enclosingMethod;
    }

    public static final class EnclosingMethodInfo {
        private String name;
        private Type returnType;
        private List<Type> parameters;
        private DotName enclosingClass;


        public String name() {
            return name;
        }

        public Type returnType() {
            return returnType;
        }

        public List<Type> parameters() {
            return parameters;
        }

        public DotName enclosingClass() {
            return enclosingClass;
        }

        EnclosingMethodInfo(String name, Type returnType, List<Type> parameters, DotName enclosingClass) {
            this.name = name;
            this.returnType = returnType;
            this.parameters = Collections.unmodifiableList(parameters);
            this.enclosingClass = enclosingClass;
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(returnType).append(' ').append(enclosingClass).append('.').append(name).append('(');
            for (int i = 0; i < parameters.size(); i++) {
                builder.append(parameters.get(i));
                if (i + 1 < parameters.size())
                    builder.append(", ");
            }
            builder.append(')');
            return builder.toString();
        }

    }

    ClassInfo(DotName name, DotName superName, short flags, List<DotName> interfaces, Map<DotName, List<AnnotationInstance>> annotations) {
        this(name, superName, flags, interfaces, annotations, false);
    }

    ClassInfo(DotName name, DotName superName, short flags, List<DotName> interfaces, Map<DotName, List<AnnotationInstance>> annotations, boolean hasNoArgsConstructor) {
        this.name = name;
        this.superName = superName;
        this.flags = flags;
        this.interfaces = Utils.emptyOrWrap(interfaces);
        this.annotations = Collections.unmodifiableMap(annotations);  // FIXME
        this.hasNoArgsConstructor = hasNoArgsConstructor;
    }

    /**
     * Constructs a "mock" ClassInfo using the passed values. All passed values MUST NOT BE MODIFIED AFTER THIS CALL.
     * Otherwise the resulting object would not conform to the contract outlined above.
     *
     * @param name the name of this class
     * @param superName the name of the parent class
     * @param flags the class attributes
     * @param interfaces the methodParameters this class implements
     * @param annotations the annotations on this class
     * @return a new mock class representation
     */
    public static ClassInfo create(DotName name, DotName superName, short flags, DotName[] interfaces, Map<DotName, List<AnnotationInstance>> annotations, boolean hasNoArgsConstructor) {
        return new ClassInfo(name, superName, flags, Arrays.asList(interfaces), annotations, hasNoArgsConstructor);
    }

    public String toString() {
        return name.toString();
    }

    public final DotName name() {
        return name;
    }

    public final short flags() {
        return flags;
    }

    public final DotName superName() {
        return superName;
    }

    @Deprecated
    public final DotName[] interfaces() {
        return interfaces.toArray(new DotName[interfaces.size()]);
    }

    public final Map<DotName, List<AnnotationInstance>> annotations() {
        return annotations;
    }

    public final Collection<AnnotationInstance> classAnnotations() {
        return new AnnotationTargetFilterCollection<ClassInfo>(annotations, ClassInfo.class);
    }

    public final List<MethodInfo> methods() {
        return new MethodInfoGenerator(this, methods);
    }

    public final MethodInfo method(String name, Type... parameters) {
        MethodInternal key = new MethodInternal(Utils.toUTF8(name), Arrays.asList(parameters), null, (short) 0);
        int i = Arrays.binarySearch(methods, key, MethodInternal.NAME_AND_PARAMETER_COMPONENT_COMPARATOR);
        return i >= 0 ? new MethodInfo(this, methods[i]) : null;
    }

    public final MethodInfo firstMethod(String name) {
        MethodInternal key = new MethodInternal(Utils.toUTF8(name), Collections.<Type>emptyList(), null, (short) 0);
        int i = Arrays.binarySearch(methods, key, MethodInternal.NAME_AND_PARAMETER_COMPONENT_COMPARATOR);
        if (i < -methods.length) {
            return null;
        }

        MethodInfo method = new MethodInfo(this,i >= 0 ? methods[i] : methods[++i * -1]);
        return method.name().equals(name) ? method : null;
    }

    public final List<FieldInfo> fields() {
        return new FieldInfoGenerator(this, fields);
    }

    public final List<DotName> interfaceNames() {
        return interfaces;
    }

    public final List<Type> interfaceTypes() {
        return Collections.unmodifiableList(Arrays.asList(interfaceTypes));
    }

    final Type[] copyInterfaceTypes() {
        return interfaceTypes.clone();
    }

    public final Type superClassType() {
        return superClassType;
    }

    public final List<Type> typeParameters() {
        return Collections.unmodifiableList(Arrays.asList(typeParameters));
    }

    final Type[] typeParameterArray() {
        return typeParameters;
    }

    /**
     * Returns a boolean indicating the presence of a no-arg constructor, if supported by the underlying index store.
     * This information is available in indexes produced by Jandex 1.2.0 and later.
     *
     * @return <code>true</code> in case of the Java class has a no-copyParameters constructor, <code>false</code>
     *         if it does not, or it is not known
     * @since 1.2.0
     */
    public final boolean hasNoArgsConstructor() {
        return hasNoArgsConstructor;
    }

    public NestingType nestingType() {
        if (nestingInfo == null) {
            return NestingType.TOP_LEVEL;
        } else if (nestingInfo.enclosingClass != null) {
            return NestingType.INNER;
        } else if (nestingInfo.simpleName != null) {
            return NestingType.LOCAL;
        }

        return NestingType.ANONYMOUS;
    }

    public String simpleName() {
        return nestingInfo != null ? nestingInfo.simpleName : null;
    }

    public DotName enclosingClass() {
        return nestingInfo != null ? nestingInfo.enclosingClass : null;
    }

    public EnclosingMethodInfo enclosingMethod() {
        return nestingInfo != null ? nestingInfo.enclosingMethod : null;
    }

    /** Lazily initialize hasNoArgsConstructor. Can only be called before publication */
    void setHasNoArgsConstructor(boolean hasNoArgsConstructor) {
        this.hasNoArgsConstructor = hasNoArgsConstructor;
    }

    void setFields(List<FieldInfo> fields, NameTable names) {
        if (fields.size() == 0) {
            this.fields = FieldInternal.EMPTY_ARRAY;
            return;
        }
        this.fields = new FieldInternal[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            FieldInfo fieldInfo = fields.get(i);
            FieldInternal internal = names.intern(fieldInfo.fieldInternal());
            fieldInfo.setFieldInternal(internal);
            this.fields[i] = internal;
        }
        Arrays.sort(this.fields, FieldInternal.NAME_COMPARATOR);
    }

    void setMethods(List<MethodInfo> methods, NameTable names) {
        if (methods.size() == 0) {
            this.methods = MethodInternal.EMPTY_ARRAY;
            return;
        }

        this.methods = new MethodInternal[methods.size()];
        for (int i = 0; i < methods.size(); i++) {
            MethodInfo methodInfo = methods.get(i);
            MethodInternal internal = names.intern(methodInfo.methodInternal());
            methodInfo.setMethodInternal(internal);
            this.methods[i] = internal;
        }
        Arrays.sort(this.methods, MethodInternal.NAME_AND_PARAMETER_COMPONENT_COMPARATOR);
    }

    void setSuperClassType(Type superClassType) {
        this.superClassType = superClassType;
    }

    void setInterfaceTypes(Type[] interfaceTypes) {
        this.interfaceTypes = interfaceTypes.length == 0 ? Type.EMPTY_ARRAY : interfaceTypes;
    }

    void setTypeParameters(Type[] typeParameters) {
        this.typeParameters = typeParameters.length == 0 ? Type.EMPTY_ARRAY : typeParameters;
    }

    void setInnerClassInfo(DotName enclosingClass, String simpleName) {
        if (nestingInfo == null) {
            nestingInfo = new NestingInfo();
        }

        nestingInfo.enclosingClass = enclosingClass;
        nestingInfo.simpleName = simpleName;
    }

    void setEnclosingMethod(EnclosingMethodInfo enclosingMethod) {
        if (nestingInfo == null) {
            nestingInfo = new NestingInfo();
        }

        nestingInfo.enclosingMethod = enclosingMethod;
    }
}
