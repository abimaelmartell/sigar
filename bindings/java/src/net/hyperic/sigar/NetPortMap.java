package net.hyperic.sigar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.File;
import java.io.FileReader;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

public class NetPortMap {

    private static final String SERVICE_FILE;

    private static Map udpServices = null;
    private static Map tcpServices = null;

    static {
        String defaultFile;

        if (SigarLoader.IS_WIN32) {
            defaultFile = "C:\\windows\\system32\\drivers\\etc\\services";
        }
        else {
            defaultFile = "/etc/services";
        }

        SERVICE_FILE =
            System.getProperty("sigar.net.services.file", defaultFile);
    }

    public static class IpEntry {
        int count;

        IpEntry() {
            this.count = 0;
        }

        public String toString() {
            return "count:" + this.count;
        }
    }

    private static void parseServices(String type, Map services) {
        File file = new File(SERVICE_FILE);
        if (!file.exists()) {
            return;
        }

        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String name, protocol;
                Long port;

                line = line.trim();
                if ((line.length() == 0) || (line.charAt(0) == '#')) {
                    continue;
                }

                StringTokenizer st = new StringTokenizer(line, " \t/#");
                if (st.countTokens() < 3) {
                    continue;
                }
                name = st.nextToken().trim();
                String pnum = st.nextToken().trim();
                protocol = st.nextToken().trim();
                if (!type.equals(protocol)) {
                    continue;
                }
                services.put(Long.valueOf(pnum), name);
            }
        } catch (IOException e) {
            return;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) { }
            }
        }
    }

    public static String getServiceName(String protocol, long port) {
        if (protocol.equals("tcp")) {
            return getTcpServiceName(port);
        }
        else if (protocol.equals("udp")) {
            return getUdpServiceName(port);
        }
        else {
            return String.valueOf(port);
        }
    }

    public static String getTcpServiceName(long port) {
        if (tcpServices == null) {
            tcpServices = new HashMap();
            parseServices("tcp", tcpServices);
        }
        return (String)tcpServices.get(new Long(port));
    }

    public static String getUdpServiceName(long port) {
        if (udpServices == null) {
            udpServices = new HashMap();
            parseServices("udp", udpServices);
        }
        return (String)udpServices.get(new Long(port));
    }

    /**
     * Map listening tcp ports to connected remote addresses.
     * key == Listening tcp port on the local machine.
     * value == List of connected remote addresses.
     */
    public static Map getTcpConnections(Sigar sigar)
        throws SigarException {

        int flags =
            NetFlags.CONN_SERVER | NetFlags.CONN_TCP;

        Map map = new HashMap();

        NetConnection[] connections =
            sigar.getNetConnectionList(flags);

        //first pass, get listening port numbers
        for (int i=0; i<connections.length; i++) {
            NetConnection conn = connections[i];
            Long port = new Long(conn.getLocalPort());
            Map addresses = (Map)map.get(port);

            if (addresses == null) {
                addresses = new HashMap();
                map.put(port, addresses);
            }
        }

        //second pass, get addresses connected to listening ports
        flags = NetFlags.CONN_CLIENT | NetFlags.CONN_TCP;
        connections = sigar.getNetConnectionList(flags);
        IpEntry key = new IpEntry();

        for (int i=0; i<connections.length; i++) {
            NetConnection conn = connections[i];
            Long port = new Long(conn.getLocalPort());
            Map addresses = (Map)map.get(port);

            if (addresses == null) {
                continue;
            }

            String ip = conn.getRemoteAddress();
            IpEntry entry = (IpEntry)addresses.get(ip);
            if (entry == null) {
                entry = new IpEntry();
                addresses.put(ip, entry);
            }
            entry.count++;
        }

        return map;
    }

    public static void main(String[] args) throws Exception {
        Sigar sigar = new Sigar();
        Map ports = getTcpConnections(sigar);

        for (Iterator it = ports.entrySet().iterator();
             it.hasNext();)
        {
            Map.Entry entry = (Map.Entry)it.next();
            Long port = (Long)entry.getKey();
            Map addresses = (Map)entry.getValue();
            System.out.println(port + "=" + addresses);
        }
    }
}