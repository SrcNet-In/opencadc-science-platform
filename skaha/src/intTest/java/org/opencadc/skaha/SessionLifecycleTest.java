/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2020.                            (c) 2020.
 *  Government of Canada                 Gouvernement du Canada
 *  National Research Council            Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 *  All rights reserved                  Tous droits réservés
 *
 *  NRC disclaims any warranties,        Le CNRC dénie toute garantie
 *  expressed, implied, or               énoncée, implicite ou légale,
 *  statutory, of any kind with          de quelque nature que ce
 *  respect to the software,             soit, concernant le logiciel,
 *  including without limitation         y compris sans restriction
 *  any warranty of merchantability      toute garantie de valeur
 *  or fitness for a particular          marchande ou de pertinence
 *  purpose. NRC shall not be            pour un usage particulier.
 *  liable in any event for any          Le CNRC ne pourra en aucun cas
 *  damages, whether direct or           être tenu responsable de tout
 *  indirect, special or general,        dommage, direct ou indirect,
 *  consequential or incidental,         particulier ou général,
 *  arising from the use of the          accessoire ou fortuit, résultant
 *  software.  Neither the name          de l'utilisation du logiciel. Ni
 *  of the National Research             le nom du Conseil National de
 *  Council of Canada nor the            Recherches du Canada ni les noms
 *  names of its contributors may        de ses  participants ne peuvent
 *  be used to endorse or promote        être utilisés pour approuver ou
 *  products derived from this           promouvoir les produits dérivés
 *  software without specific prior      de ce logiciel sans autorisation
 *  written permission.                  préalable et particulière
 *                                       par écrit.
 *
 *  This file is part of the             Ce fichier fait partie du projet
 *  OpenCADC project.                    OpenCADC.
 *
 *  OpenCADC is free software:           OpenCADC est un logiciel libre ;
 *  you can redistribute it and/or       vous pouvez le redistribuer ou le
 *  modify it under the terms of         modifier suivant les termes de
 *  the GNU Affero General Public        la “GNU Affero General Public
 *  License as published by the          License” telle que publiée
 *  Free Software Foundation,            par la Free Software Foundation
 *  either version 3 of the              : soit la version 3 de cette
 *  License, or (at your option)         licence, soit (à votre gré)
 *  any later version.                   toute version ultérieure.
 *
 *  OpenCADC is distributed in the       OpenCADC est distribué
 *  hope that it will be useful,         dans l’espoir qu’il vous
 *  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
 *  without even the implied             GARANTIE : sans même la garantie
 *  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
 *  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
 *  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
 *  General Public License for           Générale Publique GNU Affero
 *  more details.                        pour plus de détails.
 *
 *  You should have received             Vous devriez avoir reçu une
 *  a copy of the GNU Affero             copie de la Licence Générale
 *  General Public License along         Publique GNU Affero avec
 *  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
 *  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
 *                                       <http://www.gnu.org/licenses/>.
 *
 ************************************************************************
 */

package org.opencadc.skaha;

import ca.nrc.cadc.auth.AuthMethod;
import ca.nrc.cadc.reg.Standards;
import ca.nrc.cadc.reg.client.RegistryClient;
import ca.nrc.cadc.util.Log4jInit;
import java.net.URL;
import java.security.PrivilegedExceptionAction;
import javax.security.auth.Subject;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.opencadc.skaha.session.Session;
import org.opencadc.skaha.session.SessionAction;

/**
 * Integration test for session lifecycle creation and deletion.
 *
 * @author majorb
 */
public class SessionLifecycleTest {

    private static final Logger log = Logger.getLogger(SessionLifecycleTest.class);

    static {
        Log4jInit.setLevel("org.opencadc.skaha", Level.INFO);
    }

    protected final URL sessionURL;
    protected final Subject userSubject;

    public SessionLifecycleTest() throws Exception {
        RegistryClient regClient = new RegistryClient();
        final URL sessionServiceURL =
                regClient.getServiceURL(SessionUtil.getSkahaServiceID(), Standards.PROC_SESSIONS_10, AuthMethod.TOKEN);
        sessionURL = new URL(sessionServiceURL.toString() + "/session");
        log.info("sessions URL: " + sessionURL);

        this.userSubject = SessionUtil.getCurrentUser(sessionURL, false);
        log.debug("userSubject: " + userSubject);
    }

    @Test
    public void testCreateDeleteSessions() throws Exception {
        Subject.doAs(userSubject, (PrivilegedExceptionAction<Object>) () -> {

            // ensure that there is no active session
            SessionUtil.initializeCleanup(this.sessionURL);

            // create desktop session
            final String desktopSessionID = SessionUtil.createSession(
                    this.sessionURL,
                    "inttest" + SessionAction.SESSION_TYPE_DESKTOP,
                    SessionUtil.getImageOfType(SessionAction.SESSION_TYPE_DESKTOP)
                            .getId(),
                    SessionAction.SESSION_TYPE_DESKTOP);

            final Session desktopSession =
                    SessionUtil.waitForSession(this.sessionURL, desktopSessionID, Session.STATUS_RUNNING);
            SessionUtil.verifySession(
                    desktopSession, SessionAction.SESSION_TYPE_DESKTOP, "inttest" + SessionAction.SESSION_TYPE_DESKTOP);

            // create carta session
            final String cartaSessionID = SessionUtil.createSession(
                    sessionURL,
                    "inttest" + SessionAction.SESSION_TYPE_CARTA,
                    SessionUtil.getImageOfType(SessionAction.SESSION_TYPE_CARTA).getId(),
                    SessionAction.SESSION_TYPE_CARTA);
            Session cartaSession = SessionUtil.waitForSession(sessionURL, cartaSessionID, Session.STATUS_RUNNING);
            SessionUtil.verifySession(
                    desktopSession, SessionAction.SESSION_TYPE_CARTA, "inttest" + SessionAction.SESSION_TYPE_CARTA);

            Assert.assertNotNull("CARTA session not running.", cartaSession);
            Assert.assertEquals(
                    "CARTA session name is wrong",
                    "inttest" + SessionAction.SESSION_TYPE_CARTA,
                    cartaSession.getName());
            Assert.assertNotNull("CARTA session id is null", cartaSession.getId());
            Assert.assertNotNull("CARTA connect URL is null", cartaSession.getConnectURL());
            Assert.assertNotNull("CARTA up since is null", cartaSession.getStartTime());

            // verify both desktop and carta sessions
            Assert.assertNotNull("no desktop session", desktopSessionID);
            Assert.assertNotNull("no carta session", cartaSessionID);

            // delete desktop session
            SessionUtil.deleteSession(sessionURL, desktopSessionID);

            cartaSession = SessionUtil.waitForSession(sessionURL, cartaSessionID, Session.STATUS_RUNNING);
            // verify remaining carta session
            Assert.assertNotNull("CARTA Session should still be running.", cartaSession);

            // delete carta session
            SessionUtil.deleteSession(sessionURL, cartaSessionID);

            return null;
        });
    }
}
