/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.publish.maven
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest

class MavenPublishCoordinatesIntegTest extends AbstractMavenPublishIntegTest {

    def "can publish with specified coordinates"() {
        given:
        using m2

        def repoModule = javaLibrary(mavenRepo.module('org.custom', 'custom', '2.2'))
        def localModule = javaLibrary(m2.mavenRepo().module('org.custom', 'custom', '2.2'))

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                        groupId 'org.custom'
                        artifactId 'custom'
                        version '2.2'
                    }
                }
            }
        """

        when:
        succeeds 'publishToMavenLocal'

        then: "jar is published to maven local repository"
        repoModule.assertNotPublished()
        localModule.assertPublished()

        when:
        succeeds 'publish'

        then: "jar is published to defined maven repository"
        file('build/libs/root-1.0.jar').assertExists()

        and:
        repoModule.assertPublished()

        and:
        resolveArtifacts(repoModule) == ['custom-2.2.jar']
    }

    def "can produce multiple separate publications for single project"() {
        given:
        def module = mavenRepo.module('org.custom', 'custom', '2.2').withModuleMetadata()
        def apiModule = mavenRepo.module('org.custom', 'custom-api', '2')

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            task apiJar(type: Jar) {
                from sourceSets.main.output
                baseName "root-api"
                exclude "**/impl/**"
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    impl(MavenPublication) {
                        groupId "org.custom"
                        artifactId "custom"
                        version "2.2"
                        from components.java
                    }
                    api(MavenPublication) {
                        groupId "org.custom"
                        artifactId "custom-api"
                        version "2"
                        artifact(apiJar)
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        file('build/libs').assertHasDescendants("root-1.0.jar", "root-api-1.0.jar")

        and:
        module.assertPublishedAsJavaModule()
        module.moduleDir.file('custom-2.2.jar').assertIsCopyOf(file('build/libs/root-1.0.jar'))

        and:
        apiModule.assertPublishedAsJavaModule()
        apiModule.moduleDir.file('custom-api-2.jar').assertIsCopyOf(file('build/libs/root-api-1.0.jar'))

        and:
        resolveArtifacts(module) == ['custom-2.2.jar']
        resolveArtifacts(apiModule) == ['custom-api-2.jar']
    }

}
