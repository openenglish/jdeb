/*
 * Copyright 2013 The jdeb developers.
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
package org.vafer.jdeb.maven;

import static org.vafer.jdeb.maven.MissingSourceBehavior.FAIL;
import static org.vafer.jdeb.maven.MissingSourceBehavior.IGNORE;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.maven.artifact.DefaultArtifact;
import org.vafer.jdeb.DataConsumer;
import org.vafer.jdeb.DataProducer;
import org.vafer.jdeb.producers.DataProducerArchive;
import org.vafer.jdeb.producers.DataProducerDirectory;
import org.vafer.jdeb.producers.DataProducerFile;
import org.vafer.jdeb.producers.DataProducerLink;
import org.vafer.jdeb.producers.DataProducerPathTemplate;

/**
 * Maven "data" element acting as a factory for DataProducers. So far Archive and
 * Directory producers are supported. Both support the usual ant pattern set
 * matching.
 *
 * @author Bryan Sant
 */
public final class Data implements DataProducer {

    private File src;

    /**
     * @parameter expression="${src}"
     */
    public void setSrc( File src ) {
        this.src = src;
    }

    private String dst;

    /**
     * @parameter expression="${dst}"
     */
    public void setDst( String dst ) {
        this.dst = dst;
    }

    private String type;

    /**
     * @parameter expression="${type}"
     */
    public void setType( String type ) {
        this.type = type;
    }

    private MissingSourceBehavior missingSrc = FAIL;

    /**
     * @parameter expression="${missingSrc}"
     */
    public void setMissingSrc( String missingSrc ) {
        MissingSourceBehavior value = MissingSourceBehavior.valueOf(missingSrc.trim().toUpperCase());
        if (value == null) {
            throw new IllegalArgumentException("Unknown " + MissingSourceBehavior.class.getSimpleName() + ": " + missingSrc);
        }
        this.missingSrc = value;
    }

    private String artifact;

    /**
     * @parameter expression="${artifact}"
     */
    public void setArtifact(String artifact) {
        this.artifact = artifact;
    }

    private String linkName;

    /**
     * @parameter expression="${linkName}"
     */
    public void setLinkName(String linkName) {
        this.linkName = linkName;
    }

    private String linkTarget;

    /**
     * @parameter expression="${linkTarget}"
     */
    public void setLinkTarget(String linkTarget) {
        this.linkTarget = linkTarget;
    }

    private boolean symlink = true;

    /**
     * @parameter expression="${symlink}"
     */
    public void setSymlink(boolean symlink) {
        this.symlink = symlink;
    }

    private String[] includePatterns;

    /**
     * @parameter expression="${includes}" alias="includes"
     */
    public void setIncludes( String includes ) {
        includePatterns = splitPatterns(includes);
    }

    private String[] excludePatterns;

    /**
     * @parameter expression="${excludes}" alias="excludes"
     */
    public void setExcludes( String excludes ) {
        excludePatterns = splitPatterns(excludes);
    }

    /**
     * @parameter expression="${mapper}"
     */
    private Mapper mapper;

    private List<DefaultArtifact> pluginArtifacts = new ArrayList<DefaultArtifact>();

    public void setPluginArtifacts(List pluginArtifacts) {
        this.pluginArtifacts = pluginArtifacts;
    }

    /**
     * @parameter expression="${paths}"
     */
    private String[] paths;

    /* For testing only */
    void setPaths( String[] paths ) {
        this.paths = paths;
    }

    public String[] splitPatterns( String patterns ) {
        String[] result = null;
        if (patterns != null && patterns.length() > 0) {
            List<String> tokens = new ArrayList<String>();
            StringTokenizer tok = new StringTokenizer(patterns, ", ", false);
            while (tok.hasMoreTokens()) {
                tokens.add(tok.nextToken());
            }
            result = tokens.toArray(new String[tokens.size()]);
        }
        return result;
    }

    public void produce( final DataConsumer pReceiver ) throws IOException {
        org.vafer.jdeb.mapping.Mapper[] mappers = null;
        if (mapper != null) {
            mappers = new org.vafer.jdeb.mapping.Mapper[] { mapper.createMapper() };
        }

        // artifact type

        if ("dependency".equalsIgnoreCase(type)) {
            if (artifact == null) {
                throw new RuntimeException("artifact is not set");
            }
            if (!artifact.contains(":")) {
                throw new RuntimeException("artifact not defined correctly, it needs to be in the form: groupId:artifactId");
            }

            boolean found = false;
            for (DefaultArtifact artifact : pluginArtifacts) {
                String groupArtifact = String.format("%s:%s", artifact.getGroupId(), artifact.getArtifactId());
                if (groupArtifact.equals(this.artifact)) {
                    // found the artifact we are looking for
                    src = artifact.getFile();
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new RuntimeException("could not find dependency: " + artifact + " in project, did you include it as a dependency?");
            }

            new DataProducerFile(src, dst, includePatterns, excludePatterns, mappers).produce(pReceiver);
            return;
        }

        // link type

        if ("link".equalsIgnoreCase(type)) {
            if (linkName == null) {
                throw new RuntimeException("linkName is not set");
            }
            if (linkTarget == null) {
                throw new RuntimeException("linkTarget is not set");
            }

            new DataProducerLink(linkName, linkTarget, symlink, includePatterns, excludePatterns, mappers).produce(pReceiver);
            return;
        }

        // template type

        if ("template".equalsIgnoreCase(type)) {
            if (paths == null || paths.length == 0) {
                throw new RuntimeException("paths is not set");
            }

            new DataProducerPathTemplate(paths, includePatterns, excludePatterns, mappers).produce(pReceiver);
            return;
        }

        // Types that require src to exist

        if (src == null || !src.exists()) {
            if (missingSrc == IGNORE) {
                return;
            } else {
                throw new FileNotFoundException("Data source not found : " + src);
            }
        }

        if ("file".equalsIgnoreCase(type)) {
            new DataProducerFile(src, dst, includePatterns, excludePatterns, mappers).produce(pReceiver);
            return;
        }

        if ("archive".equalsIgnoreCase(type)) {
            new DataProducerArchive(src, includePatterns, excludePatterns, mappers).produce(pReceiver);
            return;
        }

        if ("directory".equalsIgnoreCase(type)) {
            new DataProducerDirectory(src, includePatterns, excludePatterns, mappers).produce(pReceiver);
            return;
        }

        throw new IOException("Unknown type '" + type + "' (file|directory|archive|template|link) for " + src);
    }

}
