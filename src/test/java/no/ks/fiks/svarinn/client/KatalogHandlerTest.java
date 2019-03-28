package no.ks.fiks.svarinn.client;

import com.google.common.io.Resources;
import feign.codec.DecodeException;
import no.ks.fiks.svarinn.client.api.katalog.api.SvarInnKatalogApi;
import no.ks.fiks.svarinn.client.api.katalog.model.KatalogKonto;
import no.ks.fiks.svarinn.client.api.katalog.model.KontoStatusApiModel;
import no.ks.fiks.svarinn.client.api.katalog.model.OffentligNokkel;
import no.ks.fiks.svarinn.client.model.FiksOrgId;
import no.ks.fiks.svarinn.client.model.Identifikator;
import no.ks.fiks.svarinn.client.model.IdentifikatorType;
import no.ks.fiks.svarinn.client.model.Konto;
import no.ks.fiks.svarinn.client.model.KontoId;
import no.ks.fiks.svarinn.client.model.LookupRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KatalogHandlerTest {

    @Mock
    private SvarInnKatalogApi svarInnKatalogApi;

    @InjectMocks
    private KatalogHandler katalogHandler;

    @DisplayName("Gjør oppslag på konto")
    @Nested
    class Lookup {

        @DisplayName("ikke funnet")
        @Test
        void lookupNotFound() {
            final String identifikator = "999999999";
            final String meldingType = "melding";
            final int sikkerhetsNiva = 4;
            final IdentifikatorType identifikatorType = IdentifikatorType.ORG_NO;
            final Optional<Konto> melding = katalogHandler.lookup(LookupRequest.builder()
                                                                               .identifikator(new Identifikator(identifikatorType, identifikator))
                                                                               .sikkerhetsNiva(sikkerhetsNiva)
                                                                               .meldingType(meldingType)
                                                                               .build());
            assertFalse(melding.isPresent());
            verify(svarInnKatalogApi).lookup(eq(Arrays.asList(identifikatorType.name(), identifikator)
                                                      .stream()
                                                      .collect(
                                                          Collectors.joining("."))), eq(meldingType), eq(sikkerhetsNiva));
            verifyNoMoreInteractions(svarInnKatalogApi);
        }

        @DisplayName("funnet")
        @Test
        void lookupFound() {
            final String identifikator = "999999999";
            final String meldingType = "melding";
            final int sikkerhetsNiva = 4;
            final IdentifikatorType identifikatorType = IdentifikatorType.ORG_NO;
            final String sammensattIdentifikator = Arrays.asList(identifikatorType.name(), identifikator)
                                                         .stream()
                                                         .collect(
                                                             Collectors.joining("."));
            final Konto konto = Konto.builder()
                                     .kontoId(new KontoId(UUID.randomUUID()))
                                     .kontoNavn("Testkonto")
                                     .fiksOrgId(new FiksOrgId(UUID.randomUUID()))
                                     .fiksOrgNavn("OrgNavn")
                                     .isGyldigAvsender(true)
                                     .isGyldigMottaker(true)
                                     .build();
            when(svarInnKatalogApi.lookup(eq(sammensattIdentifikator), eq(meldingType), eq(sikkerhetsNiva))).thenReturn(new KatalogKonto().fiksOrgId(
                konto.getFiksOrgId()
                     .getFiksOrgId())
                                                                                                                                          .fiksOrgNavn(
                                                                                                                                              konto.getFiksOrgNavn())
                                                                                                                                          .kontoId(
                                                                                                                                              konto.getKontoId()
                                                                                                                                                   .getUuid())
                                                                                                                                          .kontoNavn(
                                                                                                                                              konto.getKontoNavn())
                                                                                                                                          .status(
                                                                                                                                              new KontoStatusApiModel()
                                                                                                                                                  .gyldigAvsender(
                                                                                                                                                      true)
                                                                                                                                                  .gyldigMottaker(
                                                                                                                                                      true)));
            final Optional<Konto> funnetKonto = katalogHandler.lookup(LookupRequest.builder()
                                                                                   .identifikator(new Identifikator(identifikatorType, identifikator))
                                                                                   .sikkerhetsNiva(sikkerhetsNiva)
                                                                                   .meldingType(meldingType)
                                                                                   .build());
            assertTrue(funnetKonto.isPresent());
            assertEquals(funnetKonto.get(), konto);
            verify(svarInnKatalogApi).lookup(eq(sammensattIdentifikator), eq(meldingType), eq(sikkerhetsNiva));
            verifyNoMoreInteractions(svarInnKatalogApi);
        }

    }


    @DisplayName("Hent offentlig nøkkel")
    @Nested
    class GetPublicKey {
        @DisplayName("feiler med exception")
        @Test
        void getPublicKeyFails() {
            when(svarInnKatalogApi.getOffentligNokkel(isA(UUID.class))).thenThrow(new DecodeException("Could not decode"));
            final UUID kontoId = UUID.randomUUID();
            assertThrows(DecodeException.class, () -> katalogHandler.getPublicKey(new KontoId(kontoId)));
            verify(svarInnKatalogApi).getOffentligNokkel(eq(kontoId));
            verifyNoMoreInteractions(svarInnKatalogApi);
        }

        @DisplayName("feiler under lesing av nøkkel")
        @Test
        void getPublicKeyFoundButFails() {
            when(svarInnKatalogApi.getOffentligNokkel(isA(UUID.class))).thenReturn(new OffentligNokkel().nokkel("something")
                                                                                                        .serial("0x523DC4FE")
                                                                                                        .issuerDN("CN=KS,OU=Alice,O=KS - 971032146,L=HAAKON VIISGT 9 0161 OSLO,C=NO")
                                                                                                        .subjectDN("CN=KS,OU=Alice,O=KS - 971032146,L=HAAKON VIISGT 9 0161 OSLO,C=NO")
                                                                                                        .validFrom(OffsetDateTime.now()
                                                                                                                                 .minusYears(1L))
                                                                                                        .validTo(OffsetDateTime.now()
                                                                                                                               .plusYears(1L))
            );
            final UUID kontoId = UUID.randomUUID();
            assertThrows(RuntimeException.class, () -> katalogHandler.getPublicKey(new KontoId(kontoId)));
            verify(svarInnKatalogApi).getOffentligNokkel(eq(kontoId));
            verifyNoMoreInteractions(svarInnKatalogApi);
        }

        @Test
        void getPublicKeyFoundAndValid() throws IOException {

            final byte[] certificateChunk = Resources.toByteArray(getClass().getResource("/alice-virksomhetssertifikat.crt"));


            when(svarInnKatalogApi.getOffentligNokkel(isA(UUID.class))).thenReturn(new OffentligNokkel().nokkel(new String(certificateChunk,
                                                                                                                           StandardCharsets.UTF_8))
                                                                                                        .serial("0x523DC4FE")
                                                                                                        .issuerDN("CN=KS,OU=Alice,O=KS - 971032146,L=HAAKON VIISGT 9 0161 OSLO,C=NO")
                                                                                                        .subjectDN("CN=KS,OU=Alice,O=KS - 971032146,L=HAAKON VIISGT 9 0161 OSLO,C=NO")
                                                                                                        .validFrom(OffsetDateTime.now()
                                                                                                                                 .minusYears(1L))
                                                                                                        .validTo(OffsetDateTime.now()
                                                                                                                               .plusYears(1L))
            );
            final UUID kontoId = UUID.randomUUID();
            assertNotNull(katalogHandler.getPublicKey(new KontoId(kontoId)));
        }
    }

}