/*
 * Copyright (c) 2018 Governikus KG. Licensed under the EUPL, Version 1.2 or as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work except
 * in compliance with the Licence. You may obtain a copy of the Licence at:
 * http://joinup.ec.europa.eu/software/page/eupl Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package de.governikus.eumw.poseidas.server.idprovider.config;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.governikus.eumw.eidascommon.Utils;
import de.governikus.eumw.poseidas.config.schema.EPAConnectorConfigurationType;
import de.governikus.eumw.poseidas.config.schema.ServiceProviderType;


/**
 * Wrapper for class ServiceProviderType to grant easier access to key and certificate data
 * 
 * @author tt
 */
public class ServiceProviderDto extends AbstractConfigDto<ServiceProviderType>
{

  private static final Log LOG = LogFactory.getLog(ServiceProviderDto.class);

  private X509Certificate signatureCert, signatureCert2;

  private EPAConnectorConfigurationDto epaConnectorConfigurationDTO;

  /**
   * create a new empty instance
   * 
   * @param entityID primary key
   */
  public ServiceProviderDto(String entityID)
  {
    super(new ServiceProviderType());
    jaxbConfig.setEntityID(entityID);
    jaxbConfig.setEnabled(Boolean.FALSE);
  }

  /**
   * create an instance wrapping a given JAXB configuration object
   * 
   * @param jaxbConfig
   */
  ServiceProviderDto(ServiceProviderType jaxbConfig)
  {
    super(jaxbConfig);
  }

  @Override
  protected void setJaxbConfig(ServiceProviderType jaxbConfig)
  {
    this.jaxbConfig = jaxbConfig;
    try
    {
      signatureCert = Utils.readCert(jaxbConfig.getSignatureCert());
      signatureCert2 = Utils.readCert(jaxbConfig.getSignatureCert2());
    }
    catch (CertificateException e)
    {
      LOG.error("cannot read certificate", e);
    }
    if (jaxbConfig.getEPAConnectorConfiguration() != null)
    {
      epaConnectorConfigurationDTO = new EPAConnectorConfigurationDto(jaxbConfig.getEPAConnectorConfiguration(),
                                                                      this);
    }
  }

  /**
   * update and return the wrapped JAXB object
   */
  @Override
  public ServiceProviderType getJaxbConfig()
  {
    jaxbConfig.setEPAConnectorConfiguration(epaConnectorConfigurationDTO == null ? null
      : epaConnectorConfigurationDTO.getJaxbConfig());
    return jaxbConfig;
  }

  /**
   * Return the certificate to check that service providers signature against.
   */
  public X509Certificate getSignatureCert()
  {
    return signatureCert;
  }

  /**
   * Return the second certificate to check that service providers signature against.
   */
  public X509Certificate getSignatureCert2()
  {
    return signatureCert2;
  }

  /**
   * @see #getSignatureCert()
   */
  public void setSignatureCert(X509Certificate signatureCert)
  {
    this.signatureCert = signatureCert;
    try
    {
      jaxbConfig.setSignatureCert(signatureCert == null ? null : signatureCert.getEncoded());
    }
    catch (CertificateEncodingException e)
    {
      LOG.error("cannot happen because certificate comes into system encoded", e);
    }
  }

  /**
   * @see #getSignatureCert2()
   */
  public void setSignatureCert2(X509Certificate signatureCert2)
  {
    this.signatureCert2 = signatureCert2;
    try
    {
      jaxbConfig.setSignatureCert2(signatureCert2 == null ? null : signatureCert2.getEncoded());
    }
    catch (CertificateEncodingException e)
    {
      LOG.error("cannot happen because certificate comes into system encoded", e);
    }
  }

  /**
   * return the configuration object for passport based authentication
   */
  public EPAConnectorConfigurationDto getEpaConnectorConfiguration()
  {
    return epaConnectorConfigurationDTO;
  }

  /**
   * @see #getEpaConnectorConfiguration()
   */
  public void setEpaConnectorConfiguration(EPAConnectorConfigurationDto epaConnectorConfiguration)
  {
    epaConnectorConfigurationDTO = epaConnectorConfiguration;
  }

  /**
   * Return the name of this service provider.s
   */
  public String getEntityID()
  {
    return jaxbConfig.getEntityID();
  }

  /**
   * Return the name of this service provider.s
   */
  public void setEntityID(String entityID)
  {
    jaxbConfig.setEntityID(entityID);
  }

  /**
   * Return true if the represented provider is allowed to use this server. Only in this case, this
   * configuration part should be included into the warmup test.
   */
  public boolean getEnabled()
  {
    return jaxbConfig.isEnabled();
  }

  /**
   * @see #getEnabled()
   */
  public void setEnabled(boolean value)
  {
    jaxbConfig.setEnabled(Boolean.valueOf(value));
  }

  /**
   * Set several attributes which are probably shared between service providers as set in the given object.
   * 
   * @param other
   */
  public void setDefaultValues(ServiceProviderDto other)
  {
    if (other == null)
    {
      return;
    }
    if (other.getEpaConnectorConfiguration() != null)
    {
      setEpaConnectorConfiguration(new EPAConnectorConfigurationDto(new EPAConnectorConfigurationType(),
                                                                    this));
      getEpaConnectorConfiguration().setDefaultValues(other.getEpaConnectorConfiguration());
    }
  }
}
