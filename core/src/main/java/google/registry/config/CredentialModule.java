// Copyright 2018 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;
import google.registry.keyring.api.KeyModule.Key;
import google.registry.util.GoogleCredentialsBundle;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;
import javax.inject.Singleton;

/** Dagger module that provides all {@link GoogleCredentials} used in the application. */
@Module
public abstract class CredentialModule {

  /**
   * Provides the default {@link GoogleCredentialsBundle} from the Google Cloud runtime.
   *
   * <p>The credential returned depends on the runtime environment:
   *
   * <ul>
   *   <li>On AppEngine, returns the service account credential for
   *       PROJECT_ID@appspot.gserviceaccount.com
   *   <li>On Compute Engine, returns the service account credential for
   *       PROJECT_NUMBER-compute@developer.gserviceaccount.com
   *   <li>On end user host, this returns the credential downloaded by gcloud. Please refer to <a
   *       href="https://cloud.google.com/sdk/gcloud/reference/auth/application-default/login">Cloud
   *       SDK documentation</a> for details.
   * </ul>
   */
  @DefaultCredential
  @Provides
  @Singleton
  public static GoogleCredentialsBundle provideDefaultCredential(
      @Config("defaultCredentialOauthScopes") ImmutableList<String> requiredScopes) {
    GoogleCredentials credential;
    try {
      credential = GoogleCredentials.getApplicationDefault();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (credential.createScopedRequired()) {
      credential = credential.createScoped(requiredScopes);
    }
    return GoogleCredentialsBundle.create(credential);
  }

  /**
   * Provides the default {@link GoogleCredential} from the Google Cloud runtime for G Suite
   * Drive API.
   * TODO(b/138195359): Deprecate this credential once we figure out how to use
   * {@link GoogleCredentials} for G Suite Drive API.
   */
  @GSuiteDriveCredential
  @Provides
  @Singleton
  public static GoogleCredential provideGSuiteDriveCredential(
      @Config("defaultCredentialOauthScopes") ImmutableList<String> requiredScopes) {
    GoogleCredential credential;
    try {
      credential = GoogleCredential.getApplicationDefault();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (credential.createScopedRequired()) {
      credential = credential.createScoped(requiredScopes);
    }
    return credential;
  }

  /**
   * Provides a {@link GoogleCredentialsBundle} from the service account's JSON key file.
   *
   * <p>On App Engine, a thread created using Java's built-in API needs this credential when it
   * calls App Engine API. The Google Sheets API also needs this credential.
   */
  @JsonCredential
  @Provides
  @Singleton
  public static GoogleCredentialsBundle provideJsonCredential(
      @Config("defaultCredentialOauthScopes") ImmutableList<String> requiredScopes,
      @Key("jsonCredential") String jsonCredential) {
    GoogleCredentials credential;
    try {
      credential =
          GoogleCredentials.fromStream(new ByteArrayInputStream(jsonCredential.getBytes(UTF_8)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (credential.createScopedRequired()) {
      credential = credential.createScoped(requiredScopes);
    }
    return GoogleCredentialsBundle.create(credential);
  }

  /**
   * Provides a {@link GoogleCredentialsBundle} with delegated admin access for a G Suite domain.
   *
   * <p>The G Suite domain must grant delegated admin access to the registry service account with
   * all scopes in {@code requiredScopes}, including ones not related to G Suite.
   */
  @DelegatedCredential
  @Provides
  @Singleton
  public static GoogleCredentialsBundle provideDelegatedCredential(
      @Config("delegatedCredentialOauthScopes") ImmutableList<String> requiredScopes,
      @JsonCredential GoogleCredentialsBundle credentialsBundle,
      @Config("gSuiteAdminAccountEmailAddress") String gSuiteAdminAccountEmailAddress) {
    return GoogleCredentialsBundle.create(credentialsBundle
        .getGoogleCredentials()
        .createDelegated(gSuiteAdminAccountEmailAddress)
        .createScoped(requiredScopes));
  }

  /** Dagger qualifier for the Application Default Credential. */
  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface DefaultCredential {}


  /** Dagger qualifier for the credential for G Suite Drive API. */
  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface GSuiteDriveCredential {}

  /**
   * Dagger qualifier for a credential from a service account's JSON key, to be used in non-request
   * threads.
   */
  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface JsonCredential {}

  /**
   * Dagger qualifier for a credential with delegated admin access for a dasher domain (for G
   * Suite).
   */
  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface DelegatedCredential {}

  /** Dagger qualifier for the local credential used in the nomulus tool. */
  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface LocalCredential {}

  /** Dagger qualifier for the JSON string used to create the local credential. */
  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface LocalCredentialJson {}
}
