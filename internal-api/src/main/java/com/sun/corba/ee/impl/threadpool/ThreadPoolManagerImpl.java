/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.ee.impl.threadpool;

import java.io.IOException ;

import java.security.PrivilegedAction;
import java.security.AccessController ;

import java.util.concurrent.atomic.AtomicInteger ;

import com.sun.corba.ee.spi.threadpool.NoSuchThreadPoolException;
import com.sun.corba.ee.spi.threadpool.ThreadPool;
import com.sun.corba.ee.spi.threadpool.ThreadPoolManager;
import com.sun.corba.ee.spi.threadpool.ThreadPoolChooser;

public class ThreadPoolManagerImpl implements ThreadPoolManager 
{ 
    public static final String THREADPOOL_DEFAULT_NAME = "default-threadpool";

    private ThreadPool threadPool ;
    private ThreadGroup threadGroup ;

    public ThreadPoolManagerImpl() {
        threadGroup = getThreadGroup() ;
        threadPool = new ThreadPoolImpl( threadGroup,
            THREADPOOL_DEFAULT_NAME ) ;
    }

    private static AtomicInteger tgCount = new AtomicInteger() ;

    private ThreadGroup getThreadGroup() {
        ThreadGroup tg ;

        // See bugs 4916766 and 4936203
        // We intend to create new threads in a reliable thread group.
        // This avoids problems if the application/applet
        // creates a thread group, makes JavaIDL calls which create a new
        // connection and ReaderThread, and then destroys the thread
        // group. If our ReaderThreads were to be part of such destroyed thread
        // group then it might get killed and cause other invoking threads
        // sharing the same connection to get a non-restartable
        // CommunicationFailure. We'd like to avoid that.
        //
        // Our solution is to create all of our threads in the highest thread
        // group that we have access to, given our own security clearance.
        //
        try { 
            // try to get a thread group that's as high in the threadgroup  
            // parent-child hierarchy, as we can get to.
            // this will prevent an ORB thread created during applet-init from 
            // being killed when an applet dies.
            tg = AccessController.doPrivileged( 
                new PrivilegedAction<ThreadGroup>() { 
                    public ThreadGroup run() { 
                        ThreadGroup tg = Thread.currentThread().getThreadGroup() ;  
                        ThreadGroup ptg = tg ; 
                        try { 
                            while (ptg != null) { 
                                tg = ptg;  
                                ptg = tg.getParent(); 
                            } 
                        } catch (SecurityException se) { 
                            // Discontinue going higher on a security exception.
                        }
                        return new ThreadGroup(tg, "ORB ThreadGroup " + tgCount.getAndIncrement() ); 
                    } 
                }
            );
        } catch (SecurityException e) { 
            // something wrong, we go back to the original code 
            tg = Thread.currentThread().getThreadGroup(); 
        }

        return tg ;
    }
 
    public void close() {
        try {
            threadPool.close() ;
        } catch (IOException exc) {
            Exceptions.self.threadPoolCloseError() ;
        }

        try {
            boolean isDestroyed = threadGroup.isDestroyed() ;
            int numThreads = threadGroup.activeCount() ;
            int numGroups = threadGroup.activeGroupCount() ;

            if (isDestroyed) {
                Exceptions.self.threadGroupIsDestroyed( threadGroup ) ;
            } else {
                if (numThreads > 0)
                    Exceptions.self.threadGroupHasActiveThreadsInClose( threadGroup, numThreads ) ;

                if (numGroups > 0)
                    Exceptions.self.threadGroupHasSubGroupsInClose( threadGroup, numGroups ) ;

                threadGroup.destroy() ;
            }
        } catch (IllegalThreadStateException exc ) {
            Exceptions.self.threadGroupDestroyFailed( exc, threadGroup ) ;
        }

        threadGroup = null ;
    }

    /** 
    * This method will return an instance of the threadpool given a threadpoolId, 
    * that can be used by any component in the app. server. 
    *
    * @throws NoSuchThreadPoolException thrown when invalid threadpoolId is passed
    * as a parameter
    */ 
    public ThreadPool getThreadPool(String threadpoolId) 
        throws NoSuchThreadPoolException {
            
        return threadPool;
    }

    /** 
    * This method will return an instance of the threadpool given a numeric threadpoolId. 
    * This method will be used by the ORB to support the functionality of 
    * dedicated threadpool for EJB beans 
    *
    * @throws NoSuchThreadPoolException thrown when invalidnumericIdForThreadpool is passed
    * as a parameter
    */ 
    public ThreadPool getThreadPool(int numericIdForThreadpool) 
        throws NoSuchThreadPoolException { 

        return threadPool;
    }

    /** 
    * This method is used to return the numeric id of the threadpool, given a String 
    * threadpoolId. This is used by the POA interceptors to add the numeric threadpool 
    * Id, as a tagged component in the IOR. This is used to provide the functionality of 
    * dedicated threadpool for EJB beans 
    */ 
    public int  getThreadPoolNumericId(String threadpoolId) { 
        return 0;
    }

    /** 
    * Return a String Id for a numericId of a threadpool managed by the threadpool 
    * manager 
    */ 
    public String getThreadPoolStringId(int numericIdForThreadpool) {
       return "";
    } 

    /** 
    * Returns the first instance of ThreadPool in the ThreadPoolManager 
    */ 
    public ThreadPool getDefaultThreadPool() {
        return threadPool;
    }

    /**
     * Return an instance of ThreadPoolChooser based on the componentId that was
     * passed as argument
     */
    public ThreadPoolChooser getThreadPoolChooser(String componentId) {
        //FIXME: This method is not used, but should be fixed once
        //nio select starts working and we start using ThreadPoolChooser
        return null;
    }
    /**
     * Return an instance of ThreadPoolChooser based on the componentIndex that was
     * passed as argument. This is added for improved performance so that the caller
     * does not have to pay the cost of computing hashcode for the componentId
     */
    public ThreadPoolChooser getThreadPoolChooser(int componentIndex) {
        //FIXME: This method is not used, but should be fixed once
        //nio select starts working and we start using ThreadPoolChooser
        return null;
    }

    /**
     * Sets a ThreadPoolChooser for a particular componentId in the ThreadPoolManager. This 
     * would enable any component to add a ThreadPoolChooser for their specific use
     */
    public void setThreadPoolChooser(String componentId, ThreadPoolChooser aThreadPoolChooser) {
        //FIXME: This method is not used, but should be fixed once
        //nio select starts working and we start using ThreadPoolChooser
    }

    /**
     * Gets the numeric index associated with the componentId specified for a 
     * ThreadPoolChooser. This method would help the component call the more
     * efficient implementation i.e. getThreadPoolChooser(int componentIndex)
     */
    public int getThreadPoolChooserNumericId(String componentId) {
        //FIXME: This method is not used, but should be fixed once
        //nio select starts working and we start using ThreadPoolChooser
        return 0;
    }

} 

// End of file.
