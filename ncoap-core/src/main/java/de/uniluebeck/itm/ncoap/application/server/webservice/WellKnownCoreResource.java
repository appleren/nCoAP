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

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.SettableFuture;
import de.uniluebeck.itm.ncoap.message.CoapMessage;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.MessageCode;
import de.uniluebeck.itm.ncoap.message.options.ContentFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;

/**
* The .well-known/core resource is a standard webservice to be provided by every CoAP webserver as defined in
* the CoAP protocol draft. It provides a list of all available services on the server in CoRE Link Format.
*
* @author Oliver Kleine
*/
public final class WellKnownCoreResource extends NotObservableWebservice<Map<String, Webservice>> {

    private static final byte[] METHOD_NOT_ALLOWED_MESSAGE =
            "Service \"/.well-known/core\" only allows GET requests.".getBytes(CoapMessage.CHARSET);


    private static Logger log = LoggerFactory.getLogger(WellKnownCoreResource.class.getName());

    private byte[] etag;

    /**
     * Creates the well-known/core resource at path /.well-known/core as defined in the CoAP draft
     * @param initialStatus the Map containing all available path
     */
    public WellKnownCoreResource(Map<String, Webservice> initialStatus) {
        super("/.well-known/core", initialStatus, 0);
    }

    /**
     * The .well-known/core resource only allows requests with {@link MessageCode.Name#GET}. Any other code
     * returns a {@link CoapResponse} with {@link MessageCode.Name#METHOD_NOT_ALLOWED_405}.
     *
     * In case of a request with {@link @link MessageCode.Name#GET} it returns a {@link CoapResponse} with
     * {@link MessageCode.Name#CONTENT_205} and with a payload listing all paths to the available resources
     * (i.e. {@link Webservice} instances}).
     *
     * <b>Note:</b> The payload is always formatted in {@link ContentFormat#APP_LINK_FORMAT}, possibly contained
     * {@link de.uniluebeck.itm.ncoap.message.options.OptionValue.Name#ACCEPT} options in incoming {@link CoapRequest}s are ignored!
     *
     * @param responseFuture The {@link SettableFuture} to be set with a {@link CoapResponse} containing
     *                       the list of available services in CoRE link format.
     * @param coapRequest The {@link CoapRequest} to be processed by the {@link Webservice} instance
     * @param remoteEndpoint The address of the sender of the request
     *
     * @throws Exception Implementing classes may throw any {@link Exception}. Thrown {@link Exception}s cause the
     * framework to send a {@link CoapResponse} with {@link MessageCode.Name#INTERNAL_SERVER_ERROR_500} to the
     * client.
     */
    @Override
    public void processCoapRequest(SettableFuture<CoapResponse> responseFuture, CoapRequest coapRequest,
                                   InetSocketAddress remoteEndpoint) throws Exception{

            //Handle GET request
            if(coapRequest.getMessageCodeName() == MessageCode.Name.GET){
                CoapResponse response =
                        new CoapResponse(coapRequest.getMessageTypeName(), MessageCode.Name.CONTENT_205);

                response.setContent(getSerializedResourceStatus(ContentFormat.APP_LINK_FORMAT),
                        ContentFormat.APP_LINK_FORMAT);

                response.setEtag(this.etag);

                responseFuture.set(response);
            }

            //Send error response if the incoming request has a code other than GET
            else{
                CoapResponse coapResponse = new CoapResponse(coapRequest.getMessageTypeName(),
                        MessageCode.Name.METHOD_NOT_ALLOWED_405);

                coapResponse.setContent(METHOD_NOT_ALLOWED_MESSAGE, ContentFormat.TEXT_PLAIN_UTF8);
                responseFuture.set(coapResponse);
            }
    }

    @Override
    public byte[] getSerializedResourceStatus(long contentFormat){
        StringBuilder buffer = new StringBuilder();

        //TODO make this real CoRE link format
        for(String path : getResourceStatus().keySet()){
            buffer.append("<").append(path).append(">,\n");
        }

        if(buffer.length() > 3)
            buffer.deleteCharAt(buffer.length() - 2);

        log.debug("Content: \n{}", buffer.toString());

        return buffer.toString().getBytes(CoapMessage.CHARSET);
    }

    @Override
    public void shutdown() {
        //nothing to do here...
    }

    @Override
    public byte[] getEtag(long contentFormat) {
        return this.etag;
    }

    @Override
    public void updateEtag(Map<String, Webservice> resourceStatus) {
        this.etag = Ints.toByteArray(getSerializedResourceStatus(ContentFormat.APP_LINK_FORMAT).hashCode());
    }
}
