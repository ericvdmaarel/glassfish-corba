/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1996-2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 1998 IBM Corp. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.rmic;

import org.glassfish.rmic.tools.java.ClassPath;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * BatchEnvironment for rmic extends javac's version in four ways:
 * 1. It overrides errorString() to handle looking for rmic-specific
 * error messages in rmic's resource bundle
 * 2. It provides a mechanism for recording intermediate generated
 * files so that they can be deleted later.
 * 3. It holds a reference to the Main instance so that generators
 * can refer to it.
 * 4. It provides access to the ClassPath passed to the constructor.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */

@SuppressWarnings("deprecation")
public class BatchEnvironment extends org.glassfish.rmic.tools.javac.BatchEnvironment {

    /** The directory to which class files are being written. */
    private File destinationDir;

    /**
     * Create a ClassPath object for rmic from the relevant command line
     * options for class path and boot class path.
     */
    public static ClassPath createClassPath(String classPathString, String sysClassPathString) {
        /*
         * Previously, this method delegated to the
         * org.glassfish.rmic.tools.javac.BatchEnvironment.classPaths method in order
         * to supply default values for paths not specified on the
         * command line, expand extensions directories into specific
         * JAR files, and construct the ClassPath object-- but as part
         * of the fix for 6473331, which adds support for Class-Path
         * manifest entries in JAR files, those steps are now handled
         * here directly, with the help of a Path utility class copied
         * from the new javac implementation (see below).
         */
        Path path = new Path();

        if (sysClassPathString == null) {
            sysClassPathString = System.getProperty("sun.boot.class.path");
        }
        if (sysClassPathString != null) {
            path.addFiles(sysClassPathString);
        }

        /*
         * Class-Path manifest entries are supported for JAR files
         * everywhere except in the boot class path.
         */
        path.expandJarClassPaths(true);

        /*
         * In the application class path, an empty element means
         * the current working directory.
         */
        path.emptyPathDefault(".");

        if (classPathString == null) {
            // The env.class.path property is the user's CLASSPATH
            // environment variable, and it set by the wrapper (ie,
            // javac.exe).
            classPathString = System.getProperty("env.class.path");
            if (classPathString == null) {
                classPathString = ".";
            }
        }
        path.addFiles(classPathString);

        return new ClassPath(path.toArray(new String[path.size()]));
    }

    /**
     * Create a BatchEnvironment for rmic with the given class path,
     * stream for messages and destination directory.
     */
    public BatchEnvironment(OutputStream out, ClassPath path, File destinationDir) {
        super(out, path);
        this.destinationDir = destinationDir;
    }

    /**
     * Returns the directory to which generated classes will be written.
     * @return the destination directory specified by the "-d" flag
     */
    public File getDestinationDir() {
        return destinationDir;
    }

    /**
     * Get the ClassPath.
     */
    public ClassPath getClassPath() {
        return binaryPath;
    }

    /** list of generated source files created in this environment */
    private Vector<File> generatedFiles = new Vector<>();

    /**
     * Remember a generated source file generated so that it
     * can be removed later, if appropriate.
     */
    public void addGeneratedFile(File file) {
        generatedFiles.addElement(file);
    }

    /**
     * Delete all the generated source files made during the execution
     * of this environment (those that have been registered with the
     * "addGeneratedFile" method).
     */
    void deleteGeneratedFiles() {
        synchronized(generatedFiles) {
            Enumeration<File> enumeration = generatedFiles.elements();
            while (enumeration.hasMoreElements()) {
                File file = enumeration.nextElement();
                file.delete();
            }
            generatedFiles.removeAllElements();
        }
    }

    /**
     * Release resources, if any.
     */
    public void shutdown() {
        generatedFiles = null;
        super.shutdown();
    }

    /**
     * Return the formatted, localized string for a named error message
     * and supplied arguments.  For rmic error messages, with names that
     * being with "rmic.", look up the error message in rmic's resource
     * bundle; otherwise, defer to java's superclass method.
     */
    public String errorString(String err,
                              Object arg0, Object arg1, Object arg2)
    {
        if (err.startsWith("rmic.") || err.startsWith("warn.rmic.")) {
            String result =  Main.getText(err,
                                          (arg0 != null ? arg0.toString() : null),
                                          (arg1 != null ? arg1.toString() : null),
                                          (arg2 != null ? arg2.toString() : null));

            if (err.startsWith("warn.")) {
                result = "warning: " + result;
            }
            return result;
        } else {
            return super.errorString(err, arg0, arg1, arg2);
        }
    }
    public void reset() {
    }

    /**
     * Utility for building paths of directories and JAR files.  This
     * class was copied from com.org.glassfish.rmic.tools.javac.util.Paths as part of
     * the fix for 6473331, which adds support for Class-Path manifest
     * entries in JAR files.  Diagnostic code is simply commented out
     * because rmic silently ignored these conditions historically.
     */
    private static class Path extends LinkedHashSet<String> {
        private static final long serialVersionUID = 0;
        private static final boolean warn = false;

        private static class PathIterator implements Collection<String> {
            private int pos = 0;
            private final String path;
            private final String emptyPathDefault;

            public PathIterator(String path, String emptyPathDefault) {
                this.path = path;
                this.emptyPathDefault = emptyPathDefault;
            }
            public PathIterator(String path) { this(path, null); }
            public Iterator<String> iterator() {
                return new Iterator<String>() {
                    public boolean hasNext() {
                        return pos <= path.length();
                    }
                    public String next() {
                        int beg = pos;
                        int end = path.indexOf(File.pathSeparator, beg);
                        if (end == -1)
                            end = path.length();
                        pos = end + 1;

                        if (beg == end && emptyPathDefault != null)
                            return emptyPathDefault;
                        else
                            return path.substring(beg, end);
                    }
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            // required for Collection.
            public int size() {
                throw new UnsupportedOperationException();
            }
            public boolean isEmpty() {
                throw new UnsupportedOperationException();
            }
            public boolean contains(Object o) {
                throw new UnsupportedOperationException();
            }
            public Object[] toArray() {
                throw new UnsupportedOperationException();
            }
            public <T> T[] toArray(T[] a) {
                throw new UnsupportedOperationException();
            }
            public boolean add(String o) {
                throw new UnsupportedOperationException();
            }
            public boolean remove(Object o) {
                throw new UnsupportedOperationException();
            }
            public boolean containsAll(Collection<?> c) {
                throw new UnsupportedOperationException();
            }
            public boolean addAll(Collection<? extends String> c) {
                throw new UnsupportedOperationException();
            }
            public boolean removeAll(Collection<?> c) {
                throw new UnsupportedOperationException();
            }
            public boolean retainAll(Collection<?> c) {
                throw new UnsupportedOperationException();
            }
            public void clear() {
                throw new UnsupportedOperationException();
            }
            public boolean equals(Object o) {
                throw new UnsupportedOperationException();
            }
            public int hashCode() {
                throw new UnsupportedOperationException();
            }
        }

        /** Is this the name of a zip file? */
        private static boolean isZip(String name) {
            return new File(name).isFile();
        }

        private boolean expandJarClassPaths = false;

        public Path expandJarClassPaths(boolean x) {
            expandJarClassPaths = x;
            return this;
        }

        /** What to use when path element is the empty string */
        private String emptyPathDefault = null;

        public Path emptyPathDefault(String x) {
            emptyPathDefault = x;
            return this;
        }

        public Path() { super(); }

        public Path addDirectories(String dirs, boolean warn) {
            if (dirs != null)
                for (String dir : new PathIterator(dirs))
                    addDirectory(dir, warn);
            return this;
        }

        public Path addDirectories(String dirs) {
            return addDirectories(dirs, warn);
        }

        private void addDirectory(String dir, boolean warn) {
            if (! new File(dir).isDirectory()) {
//              if (warn)
//                  log.warning(Position.NOPOS,
//                              "dir.path.element.not.found", dir);
                return;
            }

            for (String direntry : new File(dir).list()) {
                String canonicalized = direntry.toLowerCase();
                if (canonicalized.endsWith(".jar") ||
                    canonicalized.endsWith(".zip"))
                    addFile(dir + File.separator + direntry, warn);
            }
        }

        public Path addFiles(String files, boolean warn) {
            if (files != null)
                for (String file : new PathIterator(files, emptyPathDefault))
                    addFile(file, warn);
            return this;
        }

        public Path addFiles(String files) {
            return addFiles(files, warn);
        }

        private void addFile(String file, boolean warn) {
            if (contains(file)) {
                /* Discard duplicates and avoid infinite recursion */
                return;
            }

            File ele = new File(file);
            if (! ele.exists()) {
                /* No such file or directory exist */
                if (warn)
//                      log.warning(Position.NOPOS,
//                          "path.element.not.found", file);
                    return;
            }

            if (ele.isFile()) {
                /* File is an ordinay file  */
                String arcname = file.toLowerCase();
                if (! (arcname.endsWith(".zip") ||
                       arcname.endsWith(".jar"))) {
                    /* File name don't have right extension */
//                      if (warn)
//                          log.warning(Position.NOPOS,
//                              "invalid.archive.file", file);
                    return;
                }
            }

            /* Now what we have left is either a directory or a file name
               confirming to archive naming convention */

            super.add(file);
            if (expandJarClassPaths && isZip(file))
                addJarClassPath(file, warn);
        }

        // Adds referenced classpath elements from a jar's Class-Path
        // Manifest entry.  In some future release, we may want to
        // update this code to recognize URLs rather than simple
        // filenames, but if we do, we should redo all path-related code.
        private void addJarClassPath(String jarFileName, boolean warn) {
            try {
                String jarParent = new File(jarFileName).getParent();
                JarFile jar = new JarFile(jarFileName);

                try {
                    Manifest man = jar.getManifest();
                    if (man == null) return;

                    Attributes attr = man.getMainAttributes();
                    if (attr == null) return;

                    String path = attr.getValue(Attributes.Name.CLASS_PATH);
                    if (path == null) return;

                    for (StringTokenizer st = new StringTokenizer(path);
                        st.hasMoreTokens();) {
                        String elt = st.nextToken();
                        if (jarParent != null)
                            elt = new File(jarParent, elt).getCanonicalPath();
                        addFile(elt, warn);
                    }
                } finally {
                    jar.close();
                }
            } catch (IOException e) {
//              log.error(Position.NOPOS,
//                        "error.reading.file", jarFileName,
//                        e.getLocalizedMessage());
            }
        }
    }
}
