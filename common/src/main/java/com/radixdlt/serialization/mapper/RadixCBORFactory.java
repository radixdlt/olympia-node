/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.serialization.mapper;

// Checkstyle disabled here, as this file has been imported from Jackson
// CHECKSTYLE:OFF:

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.format.InputAccessor;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.cbor.CBORConstants;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORParserBootstrapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;

/**
 * Factory used for constructing {@link CBORParser} and {@link CBORGenerator} instances; both of
 * which handle <a href="https://www.rfc-editor.org/info/rfc7049">CBOR</a> encoded data.
 *
 * <p>Extends {@link JsonFactory} mostly so that users can actually use it in place of regular
 * non-CBOR factory instances.
 *
 * <p>Note on using non-byte-based sources/targets (char based, like {@link Reader} and {@link
 * Writer}): these can not be used for CBOR documents; attempt will throw exception.
 *
 * <p>NOTE: This code has been adapted from the Jackson CBOR class {@link
 * com.fasterxml.jackson.dataformat.cbor.CBORFactory}. The only changes have been to change
 * references to {@code CBORGenerator} to {@link RadixCBORGenerator}.
 *
 * <p>The <a href="https://github.com/FasterXML/jackson-dataformats-binary">
 * Jackson-dataformats-binary</a> package, and therefore this file, is published under the <a
 * href="http://www.apache.org/licenses/LICENSE-2.0.txt">Apache License 2.0</a>
 *
 * @author Tatu Saloranta (original Jackson file)
 */
public class RadixCBORFactory extends JsonFactory {
  private static final long serialVersionUID = 1; // 2.6

  /*
  /**********************************************************
  /* Constants
  /**********************************************************
   */

  /** Name used to identify CBOR format. (and returned by {@link #getFormatName()} */
  public static final String FORMAT_NAME = "CBOR";

  /** Bitfield (set of flags) of all parser features that are enabled by default. */
  static final int DEFAULT_CBOR_PARSER_FEATURE_FLAGS = CBORParser.Feature.collectDefaults();

  /** Bitfield (set of flags) of all generator features that are enabled by default. */
  static final int DEFAULT_CBOR_GENERATOR_FEATURE_FLAGS =
      RadixCBORGenerator.Feature.collectDefaults();

  /*
  /**********************************************************
  /* Configuration
  /**********************************************************
   */

  protected int _formatParserFeatures;
  protected int _formatGeneratorFeatures;

  /*
  /**********************************************************
  /* Factory construction, configuration
  /**********************************************************
   */

  /**
   * Default constructor used to create factory instances. Creation of a factory instance is a
   * light-weight operation, but it is still a good idea to reuse limited number of factory
   * instances (and quite often just a single instance): factories are used as context for storing
   * some reused processing objects (such as symbol tables parsers use) and this reuse only works
   * within context of a single factory instance.
   */
  public RadixCBORFactory() {
    this(null);
  }

  public RadixCBORFactory(ObjectCodec oc) {
    super(oc);
    _formatParserFeatures = DEFAULT_CBOR_PARSER_FEATURE_FLAGS;
    _formatGeneratorFeatures = DEFAULT_CBOR_GENERATOR_FEATURE_FLAGS;
  }

  /**
   * Note: REQUIRES at least 2.2.1 -- unfortunate intra-patch dep but seems preferable to just
   * leaving bug be as is
   *
   * @since 2.2.1
   */
  public RadixCBORFactory(RadixCBORFactory src, ObjectCodec oc) {
    super(src, oc);
    _formatParserFeatures = src._formatParserFeatures;
    _formatGeneratorFeatures = src._formatGeneratorFeatures;
  }

  @Override
  public RadixCBORFactory copy() {
    _checkInvalidCopy(RadixCBORFactory.class);
    // note: as with base class, must NOT copy mapper reference
    return new RadixCBORFactory(this, null);
  }

  /*
  /**********************************************************
  /* Serializable overrides
  /**********************************************************
   */

  /**
   * Method that we need to override to actually make restoration go through constructors etc. Also:
   * must be overridden by sub-classes as well.
   */
  @Override
  protected Object readResolve() {
    return new RadixCBORFactory(this, _objectCodec);
  }

  /*
  /**********************************************************
  /* Versioned
  /**********************************************************
   */

  @Override
  public Version version() {
    return PackageVersion.VERSION;
  }

  /*
  /**********************************************************
  /* Format detection functionality
  /**********************************************************
   */

  @Override
  public String getFormatName() {
    return FORMAT_NAME;
  }

  // Defaults work fine for this:
  // public boolean canUseSchema(FormatSchema schema) { }

  @Override
  public boolean canUseCharArrays() {
    return false;
  }

  @Override
  public MatchStrength hasFormat(InputAccessor acc) throws IOException {
    return CBORParserBootstrapper.hasCBORFormat(acc);
  }

  /*
  /**********************************************************
  /* Capability introspection
  /**********************************************************
   */

  @Override
  public boolean canHandleBinaryNatively() {
    return true;
  }

  @Override // since 2.6
  public Class<CBORParser.Feature> getFormatReadFeatureType() {
    return CBORParser.Feature.class;
  }

  @Override // since 2.6
  public Class<RadixCBORGenerator.Feature> getFormatWriteFeatureType() {
    return RadixCBORGenerator.Feature.class;
  }

  /*
  /**********************************************************
  /* Configuration, parser settings
  /**********************************************************
   */

  /**
   * Method for enabling or disabling specified parser feature (check {@link CBORParser.Feature} for
   * list of features)
   */
  public final RadixCBORFactory configure(CBORParser.Feature f, boolean state) {
    if (state) {
      enable(f);
    } else {
      disable(f);
    }
    return this;
  }

  /**
   * Method for enabling specified parser feature (check {@link CBORParser.Feature} for list of
   * features)
   */
  public RadixCBORFactory enable(CBORParser.Feature f) {
    _formatParserFeatures |= f.getMask();
    return this;
  }

  /**
   * Method for disabling specified parser features (check {@link CBORParser.Feature} for list of
   * features)
   */
  public RadixCBORFactory disable(CBORParser.Feature f) {
    _formatParserFeatures &= ~f.getMask();
    return this;
  }

  /** Checked whether specified parser feature is enabled. */
  public final boolean isEnabled(CBORParser.Feature f) {
    return (_formatParserFeatures & f.getMask()) != 0;
  }

  /*
  /**********************************************************
  /* Configuration, generator settings
  /**********************************************************
   */

  /**
   * Method for enabling or disabling specified generator feature (check {@link
   * CBORGenerator.Feature} for list of features)
   */
  public final RadixCBORFactory configure(RadixCBORGenerator.Feature f, boolean state) {
    if (state) {
      enable(f);
    } else {
      disable(f);
    }
    return this;
  }

  /**
   * Method for enabling specified generator features (check {@link CBORGenerator.Feature} for list
   * of features)
   */
  public RadixCBORFactory enable(RadixCBORGenerator.Feature f) {
    _formatGeneratorFeatures |= f.getMask();
    return this;
  }

  /**
   * Method for disabling specified generator feature (check {@link CBORGenerator.Feature} for list
   * of features)
   */
  public RadixCBORFactory disable(RadixCBORGenerator.Feature f) {
    _formatGeneratorFeatures &= ~f.getMask();
    return this;
  }

  /** Check whether specified generator feature is enabled. */
  public final boolean isEnabled(RadixCBORGenerator.Feature f) {
    return (_formatGeneratorFeatures & f.getMask()) != 0;
  }

  /*
  /**********************************************************
  /* Overridden parser factory methods, new (2.1)
  /**********************************************************
   */

  @SuppressWarnings("resource")
  @Override
  public CBORParser createParser(File f) throws IOException {
    IOContext ctxt = _createContext(f, true);
    return _createParser(_decorate(new FileInputStream(f), ctxt), ctxt);
  }

  @Override
  public CBORParser createParser(URL url) throws IOException {
    IOContext ctxt = _createContext(url, true);
    return _createParser(_decorate(_optimizedStreamFromURL(url), ctxt), ctxt);
  }

  @Override
  public CBORParser createParser(InputStream in) throws IOException {
    IOContext ctxt = _createContext(in, false);
    return _createParser(_decorate(in, ctxt), ctxt);
  }

  @Override
  public CBORParser createParser(byte[] data) throws IOException {
    return createParser(data, 0, data.length);
  }

  @SuppressWarnings("resource")
  @Override
  public CBORParser createParser(byte[] data, int offset, int len) throws IOException {
    IOContext ctxt = _createContext(data, true);
    if (_inputDecorator != null) {
      InputStream in = _inputDecorator.decorate(ctxt, data, 0, data.length);
      if (in != null) {
        return _createParser(in, ctxt);
      }
    }
    return _createParser(data, offset, len, ctxt);
  }

  /*
  /**********************************************************
  /* Overridden generator factory methods
  /**********************************************************
   */

  /**
   * Method for constructing {@link JsonGenerator} for generating CBOR-encoded output.
   *
   * <p>Since CBOR format always uses UTF-8 internally, <code>enc</code> argument is ignored.
   */
  @Override
  public RadixCBORGenerator createGenerator(OutputStream out, JsonEncoding enc) throws IOException {
    final IOContext ctxt = _createContext(out, false);
    return _createCBORGenerator(
        ctxt, _generatorFeatures, _formatGeneratorFeatures, _objectCodec, _decorate(out, ctxt));
  }

  /**
   * Method for constructing {@link JsonGenerator} for generating CBOR-encoded output.
   *
   * <p>Since CBOR format always uses UTF-8 internally, no encoding need to be passed to this
   * method.
   */
  @Override
  public RadixCBORGenerator createGenerator(OutputStream out) throws IOException {
    final IOContext ctxt = _createContext(out, false);
    return _createCBORGenerator(
        ctxt, _generatorFeatures, _formatGeneratorFeatures, _objectCodec, _decorate(out, ctxt));
  }

  /*
  /******************************************************
  /* Overridden internal factory methods
  /******************************************************
   */

  @Override
  protected IOContext _createContext(Object srcRef, boolean resourceManaged) {
    return super._createContext(srcRef, resourceManaged);
  }

  /** Overridable factory method that actually instantiates desired parser. */
  @Override
  protected CBORParser _createParser(InputStream in, IOContext ctxt) throws IOException {
    return new CBORParserBootstrapper(ctxt, in)
        .constructParser(
            _factoryFeatures,
            _parserFeatures,
            _formatParserFeatures,
            _objectCodec,
            _byteSymbolCanonicalizer);
  }

  /** Overridable factory method that actually instantiates desired parser. */
  @Override
  protected JsonParser _createParser(Reader r, IOContext ctxt) throws IOException {
    return _nonByteSource();
  }

  @Override
  protected JsonParser _createParser(
      char[] data, int offset, int len, IOContext ctxt, boolean recyclable) throws IOException {
    return _nonByteSource();
  }

  /** Overridable factory method that actually instantiates desired parser. */
  @Override
  protected CBORParser _createParser(byte[] data, int offset, int len, IOContext ctxt)
      throws IOException {
    return new CBORParserBootstrapper(ctxt, data, offset, len)
        .constructParser(
            _factoryFeatures,
            _parserFeatures,
            _formatParserFeatures,
            _objectCodec,
            _byteSymbolCanonicalizer);
  }

  @Override
  protected RadixCBORGenerator _createGenerator(Writer out, IOContext ctxt) throws IOException {
    return _nonByteTarget();
  }

  @Override
  protected RadixCBORGenerator _createUTF8Generator(OutputStream out, IOContext ctxt)
      throws IOException {
    return _createCBORGenerator(
        ctxt, _generatorFeatures, _formatGeneratorFeatures, _objectCodec, out);
  }

  @Override
  protected Writer _createWriter(OutputStream out, JsonEncoding enc, IOContext ctxt)
      throws IOException {
    return _nonByteTarget();
  }

  private RadixCBORGenerator _createCBORGenerator(
      IOContext ctxt, int stdFeat, int formatFeat, ObjectCodec codec, OutputStream out)
      throws IOException {
    // false -> we won't manage the stream unless explicitly directed to
    RadixCBORGenerator gen = new RadixCBORGenerator(ctxt, stdFeat, formatFeat, _objectCodec, out);
    if (RadixCBORGenerator.Feature.WRITE_TYPE_HEADER.enabledIn(formatFeat)) {
      gen.writeTag(CBORConstants.TAG_ID_SELF_DESCRIBE);
    }
    return gen;
  }

  protected <T> T _nonByteSource() {
    throw new UnsupportedOperationException("Can not create parser for non-byte-based source");
  }

  protected <T> T _nonByteTarget() {
    throw new UnsupportedOperationException("Can not create generator for non-byte-based target");
  }
}
