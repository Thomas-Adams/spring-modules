/* 
 * Created on Nov 10, 2004
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
 *
 * Copyright @2004 the original author or authors.
 */

package org.springmodules.cache.provider.ehcache;

import java.beans.PropertyEditor;
import java.io.Serializable;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.springframework.util.ObjectUtils;

import org.springmodules.cache.CacheException;
import org.springmodules.cache.CachingModel;
import org.springmodules.cache.FatalCacheException;
import org.springmodules.cache.FlushingModel;
import org.springmodules.cache.provider.AbstractCacheProviderFacade;
import org.springmodules.cache.provider.CacheAccessException;
import org.springmodules.cache.provider.CacheModelValidator;
import org.springmodules.cache.provider.CacheNotFoundException;
import org.springmodules.cache.provider.ObjectCannotBeCachedException;
import org.springmodules.cache.provider.ReflectionCacheModelEditor;

/**
 * <p>
 * Implementation of
 * <code>{@link org.springmodules.cache.provider.CacheProviderFacade}</code>
 * that uses EHCache as the underlying cache implementation
 * </p>
 * 
 * @author Alex Ruiz
 */
public final class EhCacheFacade extends AbstractCacheProviderFacade {

  /**
   * EHCache cache manager.
   */
  private CacheManager cacheManager;

  private CacheModelValidator cacheModelValidator;

  /**
   * Constructor.
   */
  public EhCacheFacade() {
    super();
    cacheModelValidator = new EhCacheModelValidator();
  }

  /**
   * Returns the validator of cache models. It is always an instance of
   * <code>{@link EhCacheModelValidator}</code>.
   * 
   * @return the validator of cache models
   */
  public CacheModelValidator getCacheModelValidator() {
    return cacheModelValidator;
  }

  /**
   * @see org.springmodules.cache.provider.CacheProviderFacade#getCachingModelEditor()
   */
  public PropertyEditor getCachingModelEditor() {
    ReflectionCacheModelEditor editor = new ReflectionCacheModelEditor();
    editor.setCacheModelClass(EhCacheCachingModel.class);
    return editor;
  }

  /**
   * @see org.springmodules.cache.provider.CacheProviderFacade#getFlushingModelEditor()
   */
  public PropertyEditor getFlushingModelEditor() {
    ReflectionCacheModelEditor editor = new ReflectionCacheModelEditor();
    editor.setCacheModelClass(EhCacheFlushingModel.class);
    return editor;
  }

  /**
   * Sets the EHCache cache manager to use.
   * 
   * @param newCacheManager
   *          the new cache manager
   */
  public void setCacheManager(CacheManager newCacheManager) {
    cacheManager = newCacheManager;
  }

  /**
   * Returns a EHCache cache from the cache manager.
   * 
   * @param name
   *          the name of the cache
   * @return the cache retrieved from the cache manager
   * @throws CacheNotFoundException
   *           if the cache does not exist
   * @throws CacheAccessException
   *           wrapping any unexpected exception thrown by the cache
   */
  protected Cache getCache(String name) {
    Cache cache = null;

    try {
      if (cacheManager.cacheExists(name)) {
        cache = cacheManager.getCache(name);
      }
    } catch (Exception exception) {
      throw new CacheAccessException(exception);
    }

    if (cache == null) {
      throw new CacheNotFoundException(name);
    }

    return cache;
  }

  /**
   * @return <code>true</code>. EHCache can only store Serializable objects
   * @see AbstractCacheProviderFacade#isSerializableCacheElementRequired()
   */
  protected boolean isSerializableCacheElementRequired() {
    return true;
  }

  /**
   * Removes all the entries in the caches specified in the given flushing
   * model. The flushing model should be an instance of
   * <code>{@link EhCacheFlushingModel}</code>.
   * 
   * @param model
   *          the flushing model.
   * 
   * @throws CacheNotFoundException
   *           if the cache specified in the given model cannot be found.
   * @throws CacheAccessException
   *           wrapping any unexpected exception thrown by the cache.
   * @see AbstractCacheProviderFacade#onFlushCache(FlushingModel)
   */
  protected void onFlushCache(FlushingModel model) throws CacheException {
    EhCacheFlushingModel flushingModel = (EhCacheFlushingModel) model;
    String[] cacheNames = flushingModel.getCacheNames();

    if (!ObjectUtils.isEmpty(cacheNames)) {
      CacheException cacheException = null;
      int nameCount = cacheNames.length;

      try {
        for (int i = 0; i < nameCount; i++) {
          Cache cache = getCache(cacheNames[i]);
          cache.removeAll();
        }
      } catch (CacheException exception) {
        cacheException = exception;
      } catch (Exception exception) {
        cacheException = new CacheAccessException(exception);
      }

      if (cacheException != null) {
        throw cacheException;
      }
    }
  }

  /**
   * Retrieves an object stored under the given key from the cache specified in
   * the given caching model. The caching model should be an instance of
   * <code>{@link EhCacheCachingModel}</code>.
   * 
   * @param key
   *          the key of the cache entry
   * @param model
   *          the caching model
   * @return the object retrieved from the cache. Can be <code>null</code>.
   * 
   * @throws CacheNotFoundException
   *           if the cache specified in the given model cannot be found.
   * @throws CacheAccessException
   *           wrapping any unexpected exception thrown by the cache.
   * @see AbstractCacheProviderFacade#onGetFromCache(Serializable, CachingModel)
   */
  protected Object onGetFromCache(Serializable key, CachingModel model)
      throws CacheException {

    EhCacheCachingModel cachingModel = (EhCacheCachingModel) model;
    String cacheName = cachingModel.getCacheName();

    Cache cache = getCache(cacheName);
    Object cachedObject = null;

    try {
      Element cacheElement = cache.get(key);
      if (cacheElement != null) {
        cachedObject = cacheElement.getValue();
      }

    } catch (Exception exception) {
      throw new CacheAccessException(exception);
    }

    return cachedObject;
  }

  /**
   * Stores the given object under the given key in the cache specified in the
   * given caching model. The caching model should be an instance of
   * <code>{@link EhCacheCachingModel}</code>.
   * 
   * @param key
   *          the key of the cache entry
   * @param model
   *          the caching model
   * @param obj
   *          the object to store in the cache
   * 
   * @throws ObjectCannotBeCachedException
   *           if the object to store is not an implementation of
   *           <code>java.io.Serializable</code>.
   * @throws CacheNotFoundException
   *           if the cache specified in the given model cannot be found.
   * @throws CacheAccessException
   *           wrapping any unexpected exception thrown by the cache.
   * 
   * @see AbstractCacheProviderFacade#onPutInCache(Serializable, CachingModel,
   *      Object)
   */
  protected void onPutInCache(Serializable key, CachingModel model, Object obj)
      throws CacheException {

    EhCacheCachingModel cachingModel = (EhCacheCachingModel) model;
    String cacheName = cachingModel.getCacheName();

    Cache cache = getCache(cacheName);
    Element newCacheElement = new Element(key, (Serializable) obj);

    try {
      cache.put(newCacheElement);

    } catch (Exception exception) {
      throw new CacheAccessException(exception);
    }
  }

  /**
   * Removes the object stored under the given key from the cache specified in
   * the given caching model. The caching model should be an instance of
   * <code>{@link EhCacheCachingModel}</code>.
   * 
   * @param key
   *          the key of the cache entry
   * @param model
   *          the caching model
   * 
   * @throws CacheNotFoundException
   *           if the cache specified in the given model cannot be found.
   * @throws CacheAccessException
   *           wrapping any unexpected exception thrown by the cache.
   * @see AbstractCacheProviderFacade#onRemoveFromCache(Serializable,
   *      CachingModel)
   */
  protected void onRemoveFromCache(Serializable key, CachingModel model)
      throws CacheException {

    EhCacheCachingModel cachingModel = (EhCacheCachingModel) model;
    String cacheName = cachingModel.getCacheName();

    Cache cache = getCache(cacheName);

    try {
      cache.remove(key);

    } catch (Exception exception) {
      throw new CacheAccessException(exception);
    }
  }

  /**
   * @see AbstractCacheProviderFacade#validateCacheManager()
   * 
   * @throws FatalCacheException
   *           if the cache manager is <code>null</code>.
   */
  protected void validateCacheManager() throws FatalCacheException {
    assertCacheManagerIsNotNull(cacheManager);
  }

}