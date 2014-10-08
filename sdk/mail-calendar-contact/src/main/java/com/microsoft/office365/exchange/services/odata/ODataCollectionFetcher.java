/*******************************************************************************
 * Copyright (c) Microsoft Open Technologies, Inc.
 * All Rights Reserved
 * See License.txt in the project root for license information.
 ******************************************************************************/
package com.microsoft.office365.exchange.services.odata;

import com.google.common.util.concurrent.*;
import com.microsoft.office365.odata.Constants;
import com.microsoft.office365.odata.EntityFetcherHelper;
import com.microsoft.office365.odata.Helpers;
import com.microsoft.office365.odata.interfaces.*;

import static com.microsoft.office365.odata.BaseODataContainerHelper.*;
import static com.microsoft.office365.odata.EntityCollectionFetcherHelper.addListResultCallback;
import static com.microsoft.office365.odata.EntityFetcherHelper.getQueryString;
import static com.microsoft.office365.odata.EntityFetcherHelper.getSelectorUrl;
import static com.microsoft.office365.odata.Helpers.urlEncode;

import java.util.List;

public class ODataCollectionFetcher<T, U, V> extends ODataExecutable implements Executable<List<T>> {

    private int top = -1;
    private int skip = -1;
    private String selectedId = null;
    private String urlComponent;
    private ODataExecutable parent;
    private Class<T> clazz;
    private V operations;
    private String select = null;
    private String expand = null;
    private String filter = null;

    public ODataCollectionFetcher(String urlComponent, ODataExecutable parent,
                           Class<T> clazz, Class<V> operationClazz) {
        this.urlComponent = urlComponent;
        this.parent = parent;
        this.clazz = clazz;

		this.reset();

        try {
            this.operations = operationClazz.getConstructor(String.class,
                         ODataExecutable.class).newInstance("", this);
        } catch (Throwable ignored) {
        }
    }

	public void reset() {
		this.top = -1;
		this.skip = -1;
		this.selectedId = null;
		this.select = null;
		this.expand = null;
		this.filter = null;
	}

    public ODataCollectionFetcher<T, U, V> top(int top) {
        this.top = top;
        return this;
    }

    public ODataCollectionFetcher<T, U, V> skip(int skip) {
        this.skip = skip;
        return this;
    }

    public ODataCollectionFetcher<T, U, V> select(String select) {
        this.select = select;
        return this;
    }

    public ODataCollectionFetcher<T, U, V> expand(String expand) {
        this.expand = expand;
        return this;
    }

    public ODataCollectionFetcher<T, U, V> filter(String filter) {
        this.filter = filter;
        return this;
    }

    public U getById(String id) {
        this.selectedId = id;

        String[] classNameParts = (clazz.getCanonicalName() + "Fetcher").split("\\.");

        String className = "com.microsoft.office365.exchange.services.odata." + classNameParts[classNameParts.length - 1];

        try {
            Class entityQueryClass = Class.forName(className);
            ODataEntityFetcher odataEntityQuery = (ODataEntityFetcher) entityQueryClass
                    .getConstructor(String.class, ODataExecutable.class)
                    .newInstance("", this);

            return (U) odataEntityQuery;
        } catch (Throwable e) {
            // if this happens, we couldn't find the xxxQuery class at runtime.
            // this must NEVER happen
            throw new RuntimeException(e);
        }
    }

    @Override
    ListenableFuture<byte[]> oDataExecute(String path, byte[] content, HttpVerb verb) {
        if (selectedId == null) {
            String query = getQueryString(urlComponent, top, skip, select, expand, filter);
            return parent.oDataExecute(query, content, verb);
        } else {
            String query = getSelectorUrl(urlComponent, selectedId, path);
            return parent.oDataExecute(query, content, verb);
        }
    }

    @Override
    public DependencyResolver getResolver() {
        return parent.getResolver();
    }

    @Override
    public ListenableFuture<List<T>> execute() {
        final SettableFuture<List<T>> result = SettableFuture.create();
        ListenableFuture<byte[]> future = oDataExecute("", null, HttpVerb.GET);
        addListResultCallback(result, future, getResolver(), clazz);

        return result;
    }



    public ListenableFuture<T> add(T entity) {

        final SettableFuture<T> result = SettableFuture.create();
        byte[] payloadBytes = Helpers.serializeToJsonByteArray(entity, getResolver());
        ListenableFuture<byte[]> future = oDataExecute("", payloadBytes, HttpVerb.POST);

        EntityFetcherHelper.addEntityResultCallback(result, future, getResolver(), clazz);

        return result;
    }
}