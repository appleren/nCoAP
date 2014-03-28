/**
 * Copyright (c) 2012, Oliver Kleine, Institute of Telematics, University of Luebeck
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source messageCode must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/**
* Copyright (c) 2012, Oliver Kleine, Institute of Telematics, University of Luebeck
* All rights reserved
*
* Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
* following conditions are met:
*
*  - Redistributions of source code must retain the above copyright notice, this list of conditions and the following
*    disclaimer.
*
*  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
*    following disclaimer in the documentation and/or other materials provided with the distribution.
*
*  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
*    products derived from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
* INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
* INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
* GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
* LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
* OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package de.uniluebeck.itm.ncoap.application.server.webservice;

import de.uniluebeck.itm.ncoap.application.server.WebserviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import de.uniluebeck.itm.ncoap.message.options.OptionValue;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.CoapRequest;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
* This is the abstract class to be extended by classes to represent a not observable resource.The generic type T
* means, that the object that holds the resourceStatus of the resource is of type T.
*
* Example: Assume, you want to realize a not observable service representing a temperature with limited accuracy
* (integer values). Then, your service class could e.g. extend {@link NotObservableWebservice <Integer>}.
*
* @author Oliver Kleine, Stefan Hüske
*/
public abstract class NotObservableWebservice<T> implements Webservice<T> {

    private static Logger log = LoggerFactory.getLogger(NotObservableWebservice.class.getName());

    private WebserviceManager webserviceManager;

    private String path;

    private ReadWriteLock readWriteLock;

    private T resourceStatus;
    private long resourceStatusExpiryDate;

    private ScheduledExecutorService scheduledExecutorService;

    protected NotObservableWebservice(String servicePath, T initialStatus, long lifetimeSeconds){
        this.readWriteLock = new ReentrantReadWriteLock(false);
        this.path = servicePath;
        setResourceStatus(initialStatus, lifetimeSeconds);
    }


    @Override
    public final void setWebserviceManager(WebserviceManager webserviceManager){
        this.webserviceManager = webserviceManager;
    }


    @Override
    public final WebserviceManager getWebserviceManager(){
        return this.webserviceManager;
    }


    @Override
    public final String getPath() {
        return this.path;
    }


    @Override
    public final void setScheduledExecutorService(ScheduledExecutorService executorService){
        this.scheduledExecutorService = executorService;
    }


    @Override
    public ScheduledExecutorService getScheduledExecutorService(){
        return this.scheduledExecutorService;
    }


    /**
     * This method is the one and only recommended way to change the status.
     *
     * Invocation of this method write-locks the resource status, i.e. concurrent invocations of
     * {@link #getWrappedResourceStatus(long)} or this method wait for this method to
     * finish, i.e. to unlock the write-lock. This is e.g. to avoid inconsistencies between the content and
     * {@link OptionValue.Name#ETAG}, resp. {@link OptionValue.Name#MAX_AGE} in a {@link CoapResponse}. Such
     * inconsistencies could happen in case of a resource update between calls of e.g.
     * {@link #getSerializedResourceStatus(long)} and {@link #getEtag(long)}, resp. {@link #getMaxAge()}.
     *
     * @param resourceStatus the new status of the resource
     * @param lifetimeSeconds the number of seconds this status is valid, i.e. cachable by clients or proxies.
     */
    @Override
    public final void setResourceStatus(T resourceStatus, long lifetimeSeconds){
        try{
            readWriteLock.writeLock().lock();
            this.resourceStatus = resourceStatus;
            this.resourceStatusExpiryDate = System.currentTimeMillis() + (lifetimeSeconds * 1000);
            updateEtag(resourceStatus);

            log.debug("New status of {} set (expires in {} seconds).", this.path, lifetimeSeconds);
        }
        finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * This method is the one and only recommended way to retrieve the actual resource status that is used
     * for a {@link CoapResponse} to answer an incoming {@link CoapRequest}.
     *
     * Invocation of this method read-locks the resource status, i.e. concurrent invocations of
     * {@link #setResourceStatus(Object, long)} wait for this method to finish, i.e. the read-lock to be released.
     * This is to avoid inconsistencies between the content and {@link OptionValue.Name#ETAG}, resp.
     * {@link OptionValue.Name#MAX_AGE} in a {@link CoapResponse}. Such inconsistencies could happen in case of a
     * resource update between calls of e.g. {@link #getSerializedResourceStatus(long)} and {@link #getEtag(long)},
     * resp. {@link #getMaxAge()}.
     *
     * However, concurrent invocations of this method are possible, as the resources read-lock can be locked multiple
     * times in parallel.
     *
     * @param contentFormat the number representing the desired content format of the serialized resource status
     *
     * @return a {@link WrappedResourceStatus} if the content format was supported or <code>null</code> if the
     * resource status could not be serialized to the desired content format.
     */
    protected WrappedResourceStatus getWrappedResourceStatus(long contentFormat){
        try{
            this.readWriteLock.readLock().lock();

            byte[] serializedResourceStatus = getSerializedResourceStatus(contentFormat);

            if(serializedResourceStatus == null)
                return null;

            else
                return new WrappedResourceStatus(serializedResourceStatus, contentFormat,
                        this.getEtag(contentFormat), this.getMaxAge());
        }
        finally {
            this.readWriteLock.readLock().unlock();
        }
    }


    @Override
    public final T getResourceStatus(){
        return this.resourceStatus;
    }


    /**
     * Returns the number of seconds the actual resource state can be considered fresh for status caching on proxies
     * or clients. The returned number is calculated using the parameter <code>lifetimeSeconds</code> on
     * invocation of {@link #setResourceStatus(Object, long)} or {@link #NotObservableWebservice(String, Object, long)}
     * (which internally invokes {@link #setResourceStatus(Object, long)}).
     *
     * If the number of seconds passed after the last invocation of {@link #setResourceStatus(Object, long)} is larger
     * than the number of seconds given as parameter <code>lifetimeSeconds</code>, this method returns zero.
     *
     * @return the number of seconds the actual resource state can be considered fresh for status caching on proxies
     * or clients.
     */
    protected final long getMaxAge(){
        return Math.max(this.resourceStatusExpiryDate - System.currentTimeMillis(), 0) / 1000;
    }


    @Override
    public int hashCode(){
        return this.getPath().hashCode();
    }


    @Override
    public boolean equals(Object object){
        if(object == null)
            return false;

        if(!(object instanceof String || object instanceof Webservice))
            return false;

        if(object instanceof String)
            return (this.getPath().equals(object));

        return (this.getPath().equals(((Webservice) object).getPath()));
    }
}
