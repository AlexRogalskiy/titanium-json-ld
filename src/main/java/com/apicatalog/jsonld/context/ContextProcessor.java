package com.apicatalog.jsonld.context;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdErrorCode;
import com.apicatalog.jsonld.expansion.UriExpansion;
import com.apicatalog.jsonld.grammar.CompactUri;
import com.apicatalog.jsonld.grammar.Keywords;
import com.apicatalog.jsonld.grammar.Version;
import com.apicatalog.jsonld.utils.JsonUtils;
import com.apicatalog.jsonld.utils.UriUtils;

/**
 * @see <a href="https://www.w3.org/TR/json-ld11-api/#context-processing-algorithms">Context Processing Algorithm</a>
 * 
 */
public class ContextProcessor {

	// mandatory
	private final ActiveContext activeContext; 
	private JsonValue localContext; 
	private final URI baseUrl;	
	
	// optional
	private Collection<String> remoteContexts;
	
	private boolean overrideProtected;
	
	private boolean propagate;
	
	private boolean validateScopedContext;
	
	private ContextProcessor(
			ActiveContext activeContext, 
			JsonValue localContext, 
			URI baseUrl) {
		
		this.activeContext = activeContext;
		this.localContext = localContext;
		this.baseUrl = baseUrl;
		
		// default optional values
		this.remoteContexts = new ArrayList<>();
		this.overrideProtected = false;
		this.propagate = true;
		this.validateScopedContext = true;
	}

	public static final ContextProcessor with(ActiveContext activeContext, JsonValue localContext, URI baseUrl) {
		return new ContextProcessor(activeContext, localContext, baseUrl);
	}
	
	public ContextProcessor remoteContexts(Collection<String> value) {
		this.remoteContexts = value;
		return this;
	}
	
	public ContextProcessor overrideProtected(boolean value) {
		this.overrideProtected = value;
		return this;
	}
	
	public ContextProcessor propagate(boolean value) {
		this.propagate = value;
		return this;
	}
	
	public ContextProcessor validateScopedContext(boolean value) {
		this.validateScopedContext = value;
		return this;
	}

	public ActiveContext compute() throws JsonLdError {
		
		// 1. Initialize result to the result of cloning active context, with inverse context set to null.
		ActiveContext result = new ActiveContext(activeContext);
		result.inverseContext = null;
		
		// 2. If local context is an object containing the member @propagate, 
		//    its value MUST be boolean true or false, set propagate to that value.
		if (JsonUtils.isObject(localContext)) {
			
			final JsonObject localContextObject = localContext.asJsonObject();
			
			if (localContextObject.containsKey(Keywords.PROPAGATE)) {
				propagate = localContextObject.getBoolean(Keywords.PROPAGATE, this.propagate);
			}
		}

		// 3. If propagate is false, and result does not have a previous context, 
		//    set previous context in result to active context.
		if (!propagate && !result.hasPreviousContext()) {
			result.previousContext = activeContext;
		}
		
		// 4. If local context is not an array, set local context to an array containing only local context.
		if (JsonUtils.isNotArray(localContext)) {
			localContext = Json.createArrayBuilder().add(localContext).build();
		}
		
		// 5. For each item context in local context:
		for (JsonValue itemContext : localContext.asJsonArray()) {
			
			// 5.1. If context is null:
			if (JsonUtils.isNull(itemContext)) {
				
				// 5.1.1. If override protected is false and active context contains any protected term definitions,
				//       an invalid context nullification has been detected and processing is aborted.
				if (!overrideProtected && activeContext.containsProtectedTerm()) {
					throw new JsonLdError(JsonLdErrorCode.INVALID_CONTEXT_NULLIFICATION);
				}
				
				// 5.1.2. Initialize result as a newly-initialized active context, 
				//       setting both base IRI and original base URL to the value of original base URL in active context, 
				//       and, if propagate is false, previous context in result to the previous value of result.
				result =  propagate
						 	? new ActiveContext(activeContext.baseUrl, activeContext.baseUrl, activeContext.processingMode)
				 			: new ActiveContext(activeContext.baseUrl, activeContext.baseUrl, result.previousContext, activeContext.processingMode)
				 			;

				// 5.1.3. Continue with the next context 
				continue;
			} 
			
			// 5.2. if context is a string,
			if (JsonUtils.isString(itemContext)) {
				
				String contextUri = itemContext.toString();
				
				// 5.2.1
				if (baseUrl != null) {
					contextUri = UriUtils.resolve(baseUrl, ((JsonString)itemContext).getString());
				}
				
				if (UriUtils.isNotURI(contextUri)) {
					throw new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED);
				}
				
				// 5.2.2
				if (!validateScopedContext && remoteContexts.contains(contextUri)) {
					continue;
				}
				
				// 5.2.3
				remoteContexts.add(contextUri);

				// 5.2.4
				//TODO
				
				// 5.2.5
				//TODO
				
				// 5.2.6
				//TODO
				
				// 5.2.7
				continue;
			}

			// 5.3. If context is not a map, an invalid local context error has been detected and processing is aborted.
			if (JsonUtils.isNotObject(itemContext)) {
				throw new JsonLdError(JsonLdErrorCode.INVALID_LOCAL_CONTEXT);
			}

			// 5.4. Otherwise, context is a context definition
			JsonObject contextDefinition = itemContext.asJsonObject();

			// 5.5. If context has an @version
			if (contextDefinition.containsKey(Keywords.VERSION)) {

				JsonValue version = contextDefinition.get(Keywords.VERSION);
				
				String versionString = null;
				
				if (JsonUtils.isString(version)) {
					versionString = ((JsonString)version).getString();
					
				} else if (JsonUtils.isNumber(version)) {
					versionString = version.toString();
				}
								
				// 5.5.1. If the associated value is not 1.1, an invalid @version value has been detected, 
				//        and processing is aborted.
				if (versionString == null || !"1.1".equals(versionString)) {
					throw new JsonLdError(JsonLdErrorCode.INVALID_KEYWORD_VERSION_VALUE);
				}
				
				// 5.5.2. 
				if (activeContext.inMode(Version.V1_0)) {
					throw new JsonLdError(JsonLdErrorCode.PROCESSING_MODE_CONFLICT);
				}
			}
			
			// 5.6. If context has an @import
			if (contextDefinition.containsKey(Keywords.IMPORT)) {
				// 5.6.1.
				if (activeContext.inMode(Version.V1_0)) {
					throw new JsonLdError(JsonLdErrorCode.INVALID_CONTEXT_ENTRY);
				}

				JsonValue contextImport = contextDefinition.get(Keywords.IMPORT);
				// 5.6.2.
				if (JsonUtils.isNotString(contextImport)) {
					throw new JsonLdError(JsonLdErrorCode.INVALID_KEYWORD_IMPORT_VALUE);
				}
				
				// 5.6.3.
				String contextImportUri = UriUtils.resolve(baseUrl, ((JsonString)contextImport).getString());
				
				// 5.6.4.
				//TODO
				
			}
			
			// 5.7. If context has an @base entry and remote contexts is empty, 
			//		i.e., the currently being processed context is not a remote context:
			if (contextDefinition.containsKey(Keywords.BASE) && remoteContexts.isEmpty()) {
				// 5.7.1
				JsonValue value = contextDefinition.get(Keywords.BASE);
				
				// 5.7.2.
				if (JsonUtils.isNull(value)) {
					result.baseUri = null;
					
				} else {
					
					if (JsonUtils.isNotString(value)) {
						throw new JsonLdError(JsonLdErrorCode.INVALID_BASE_IRI);
					}
					
					String valueString = ((JsonString)value).getString();

					if (UriUtils.isURI(valueString) && !valueString.isBlank()) {
						
						URI uri = URI.create(valueString);

						// 5.7.3
						if (uri.isAbsolute() ) {
							 result.baseUri = uri;
							
						// 5.7.4
						} else if (result.baseUri != null) {
							
							result.baseUri = UriUtils.resolveAsUri(result.baseUri, valueString);
														
						} else {
							throw new JsonLdError(JsonLdErrorCode.INVALID_BASE_IRI);
						}
					}
				}
			}
			
			// 5.8.
			if (contextDefinition.containsKey(Keywords.VOCAB)) {
				// 5.8.1.
				JsonValue value = contextDefinition.get(Keywords.VOCAB);
				
				// 5.8.2.
				if (JsonUtils.isNull(value)) {
					result.vocabularyMapping = null;
					
				// 5.8.3
				} else {
					
					if (JsonUtils.isNotString(value)) {
						throw new JsonLdError(JsonLdErrorCode.INVALID_VOCAB_MAPPING);
					}
					
					String valueString = ((JsonString)value).getString();
					
					if (UriUtils.isURI(valueString) || CompactUri.isBlank(valueString)) {

						String vocabularyMapping = UriExpansion.with(result, valueString)
																.vocab(true)
																.documentRelative(true)
																.compute();
								
						if (UriUtils.isURI(vocabularyMapping)) {
							result.vocabularyMapping = URI.create(vocabularyMapping);
							
						} else {
							throw new JsonLdError(JsonLdErrorCode.INVALID_VOCAB_MAPPING);
						}
						
					} else {
						throw new JsonLdError(JsonLdErrorCode.INVALID_VOCAB_MAPPING);
					}
				}
			}
			
			// 5.9.
			if (contextDefinition.containsKey(Keywords.LANGUAGE)) {
				// 5.9.1
				JsonValue value = contextDefinition.get(Keywords.LANGUAGE);
				
				// 5.9.2.
				if (JsonUtils.isNull(value)) {
					result.defaultLanguage = null;
					
				// 5.9.3
				} else {
					
					if (JsonUtils.isNotString(value)) {
						throw new JsonLdError(JsonLdErrorCode.INVALID_DEFAULT_LANGUAGE);
					}
					
					result.defaultLanguage = ((JsonString)value).getString();
					//TODO check language format, generate warning if needed
				}
			}

			// 5.10.
			if (contextDefinition.containsKey(Keywords.DIRECTION)) {
				System.out.println("TODO");
				//TODO
			}
				
			// 5.11.
			if (contextDefinition.containsKey(Keywords.PROPAGATE)) {
				// 5.11.1.
				if (activeContext.inMode(Version.V1_0)) {
					throw new JsonLdError(JsonLdErrorCode.INVALID_CONTEXT_ENTRY);
				}
				// 5.11.2.
				if (JsonUtils.isNotBoolean(contextDefinition.get(Keywords.PROPAGATE))) {
					throw new JsonLdError(JsonLdErrorCode.INVALID_KEYWORD_PROPAGATE_VALUE);	
				}
			}
			
			// 5.12. Create a map defined to keep track of whether 
			//       or not a term has already been defined or is currently being defined during recursion.
			Map<String, Boolean> defined = new HashMap<>();
			
			// 5.13
			for (String key : contextDefinition.keySet()) {
				
				if (Keywords.isNot(key, 
								Keywords.BASE,
								Keywords.DIRECTION,
								Keywords.IMPORT,
								Keywords.LANGUAGE,
								Keywords.PROPAGATE,
								Keywords.PROTECTED,
								Keywords.VERSION,
								Keywords.VOCAB
								)
						) {
	
					boolean protectedFlag = contextDefinition.containsKey(Keywords.PROTECTED) 
												&& JsonUtils.isTrue(contextDefinition.get(Keywords.PROTECTED));
					
					TermDefinitionCreator
						.with(result, contextDefinition, key, defined)
						.baseUrl(baseUrl)
						.protectedFlag(protectedFlag)
						.overrideProtectedFlag(protectedFlag)
						.remoteContexts(new ArrayList<>(remoteContexts))
						.create();
				}	
			}
		}

		// 6.
		return result;
	}
	
}
