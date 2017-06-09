/**
 * Copyright 2009-2015 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.edison.mybatis.io;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.jd.edison.mybatis.logging.Log;
import com.jd.edison.mybatis.logging.LogFactory;

/**
 * <p>ResolverUtil is used to locate classes that are available in the/a class path and meet
 * arbitrary conditions. The two most common conditions are that a class implements/extends
 * another class, or that is it annotated with a specific annotation. However, through the use
 * of the {@link Test} class it is possible to search using arbitrary conditions.</p>
 * <p/>
 * <p>A ClassLoader is used to locate all locations (directories and jar files) in the class
 * path that contain classes within certain packages, and then to load those classes and
 * check them. By default the ClassLoader returned by
 * {@code Thread.currentThread().getContextClassLoader()} is used, but this can be overridden
 * by calling {@link #setClassLoader(ClassLoader)} prior to invoking any of the {@code find()}
 * methods.</p>
 * <p/>
 * <p>General searches are initiated by calling the
 * {@link #find(ResolverUtil.Test, String)} ()} method and supplying
 * a package name and a Test instance. This will cause the named package <b>and all sub-packages</b>
 * to be scanned for classes that meet the test. There are also utility methods for the common
 * use cases of scanning multiple packages for extensions of particular classes, or classes
 * annotated with a specific annotation.</p>
 * <p/>
 * <p>The standard usage pattern for the ResolverUtil class is as follows:</p>
 * <p/>
 * <pre>
 * ResolverUtil&lt;ActionBean&gt; resolver = new ResolverUtil&lt;ActionBean&gt;();
 * resolver.findImplementation(ActionBean.class, pkg1, pkg2);
 * resolver.find(new CustomTest(), pkg1);
 * resolver.find(new CustomTest(), pkg2);
 * Collection&lt;ActionBean&gt; beans = resolver.getClasses();
 * </pre>
 *
 * @author Tim Fennell
 */
public class ResolverUtil<T> {
    /*
     * An instance of Log to use for logging in this class.
     */
    private static final Log log = LogFactory.getLog(ResolverUtil.class);

    /**
     * A simple interface that specifies how to test classes to determine if they
     * are to be included in the results produced by the ResolverUtil.
     *
     * <p>
     *     一个简易过滤器
     * </p>
     */
    public static interface Test {
        /**
         * Will be called repeatedly with candidate classes. Must return True if a class
         * is to be included in the results, false otherwise.
         *
         * <p>
         *     是否匹配
         * </p>
         */
        boolean matches(Class<?> type);
    }

    /**
     * A Test that checks to see if each class is assignable to the provided class. Note
     * that this test will match the parent type itself if it is presented for matching.
     * <p>
     *     简易过滤器的实现类
     * </p>
     */
    public static class IsA implements Test {
        private Class<?> parent;

        /** Constructs an IsA test using the supplied Class as the parent class/interface. */
        /**
         * 父类类型
         * @param parentType    父类类型
         */
        public IsA(Class<?> parentType) {
            this.parent = parentType;
        }

        /** Returns true if type is assignable to the parent type supplied in the constructor. */
        /**
         * <p>
         *     判断是否匹配
         * </p>
         * <p>
         *     匹配条件：是否是指定的父类的子类
         * </p>
         * @param type      指定类型
         * @return          判断结果
         */
        @Override
        public boolean matches(Class<?> type) {
            //parent类是type类的父类，是则true,否则false;
            return type != null && parent.isAssignableFrom(type);
        }

        @Override
        public String toString() {
            return "is assignable to " + parent.getSimpleName();
        }
    }

    /**
     * A Test that checks to see if each class is annotated with a specific annotation. If it
     * is, then the test returns true, otherwise false.
     */
    public static class AnnotatedWith implements Test {
        private Class<? extends Annotation> annotation;

        /** Constructs an AnnotatedWith test for the specified annotation type. */
        public AnnotatedWith(Class<? extends Annotation> annotation) {
            this.annotation = annotation;
        }

        /** Returns true if the type is annotated with the class provided to the constructor. */
        @Override
        public boolean matches(Class<?> type) {
            return type != null && type.isAnnotationPresent(annotation);
        }

        @Override
        public String toString() {
            return "annotated with @" + annotation.getSimpleName();
        }
    }

    /** The set of matches being accumulated. */
    private Set<Class<? extends T>> matches = new HashSet<>();

    /**
     * The ClassLoader to use when looking for classes. If null then the ClassLoader returned
     * by Thread.currentThread().getContextClassLoader() will be used.
     */
    private ClassLoader classloader;

    /**
     * Provides access to the classes discovered so far. If no calls have been made to
     * any of the {@code find()} methods, this set will be empty.
     *
     * @return the set of classes that have been discovered.
     */
    public Set<Class<? extends T>> getClasses() {
        return matches;
    }

    /**
     * Returns the classloader that will be used for scanning for classes. If no explicit
     * ClassLoader has been set by the calling, the context class loader will be used.
     *
     * @return the ClassLoader that will be used to scan for classes
     */
    public ClassLoader getClassLoader() {
        return classloader == null ? Thread.currentThread().getContextClassLoader() : classloader;
    }

    /**
     * Sets an explicit ClassLoader that should be used when scanning for classes. If none
     * is set then the context classloader will be used.
     *
     * @param classloader a ClassLoader to use when scanning for classes
     */
    public void setClassLoader(ClassLoader classloader) {
        this.classloader = classloader;
    }

    /**
     * Attempts to discover classes that are assignable to the type provided. In the case
     * that an interface is provided this method will collect implementations. In the case
     * of a non-interface class, subclasses will be collected.  Accumulated classes can be
     * accessed by calling {@link #getClasses()}.
     *
     * @param parent the class of interface to find subclasses or implementations of
     * @param packageNames one or more package names to scan (including subpackages) for classes
     */
    public ResolverUtil<T> findImplementations(Class<?> parent, String... packageNames) {
        if (packageNames == null) {
            return this;
        }

        Test test = new IsA(parent);
        for (String pkg : packageNames) {
            find(test, pkg);
        }

        return this;
    }

    /**
     * Attempts to discover classes that are annotated with the annotation. Accumulated
     * classes can be accessed by calling {@link #getClasses()}.
     *
     * @param annotation the annotation that should be present on matching classes
     * @param packageNames one or more package names to scan (including subpackages) for classes
     */
    public ResolverUtil<T> findAnnotated(Class<? extends Annotation> annotation, String... packageNames) {
        if (packageNames == null) {
            return this;
        }

        Test test = new AnnotatedWith(annotation);
        for (String pkg : packageNames) {
            find(test, pkg);
        }

        return this;
    }

    /**
     * Scans for classes starting at the package provided and descending into subpackages.
     * Each class is offered up to the Test as it is discovered, and if the Test returns
     * true the class is retained.  Accumulated classes can be fetched by calling
     * {@link #getClasses()}.
     *
     * <p>
     *     1，扫描packageName下的类型
     * </p>
     * <p>
     *     2，使用{@link Test}进行过滤
     * </p>
     *
     * @param test an instance of {@link Test} that will be used to filter classes
     * @param packageName the name of the package from which to start scanning for
     *        classes, e.g. {@code net.sourceforge.stripes}
     */
    public ResolverUtil<T> find(Test test, String packageName) {
        //1，把com.jd.xxx 改为 com/jd/xxx
        String path = getPackagePath(packageName);

        try {
            //2，按照新地址查找类路径
            List<String> children = VFS.getInstance().list(path);
            for (String child : children) {
                //3，过滤非class
                if (child.endsWith(".class")) {
                    //4，按照过滤器进行过滤
                    addIfMatching(test, child);
                }
            }
        } catch (IOException ioe) {
            log.error("Could not read package: " + packageName, ioe);
        }

        return this;
    }

    /**
     * <p>
	 *     Converts a Java package name to a path that can be looked up with a call to {@link ClassLoader#getResources(String)}.
	 * </p>
     *
	 * <p>
	 *	   把一个Java包名转换为以斜杠分割的文件路径，使得可以被ClassLoader.getResources使用
	 * </p>
     * @param packageName The Java package name to convert to a path
     */
    protected String getPackagePath(String packageName) {
        return packageName == null ? null : packageName.replace('.', '/');
    }

    /**
     * Add the class designated by the fully qualified class name provided to the set of
     * resolved classes if and only if it is approved by the Test supplied.
     *
     * 通过调用{@link Test#matches(Class)} 来判断是否匹配，如果匹配的话，加入到{@link #matches}中
     * @param test the test used to determine if the class matches
     * @param fqn the fully qualified name of a class
     */
    @SuppressWarnings("unchecked")
    protected void addIfMatching(Test test, String fqn) {
        try {
            String externalName = fqn.substring(0, fqn.indexOf('.')).replace('/', '.');
            ClassLoader loader = getClassLoader();
            if (log.isDebugEnabled()) {
                log.debug("Checking to see if class " + externalName + " matches criteria [" + test + "]");
            }

            Class<?> type = loader.loadClass(externalName);
            if (test.matches(type)) {
                matches.add((Class<T>) type);
            }
        } catch (Throwable t) {
            log.warn("Could not examine class '" + fqn + "'" + " due to a " +
                    t.getClass().getName() + " with message: " + t.getMessage());
        }
    }
}