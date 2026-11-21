using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Xml.Linq;

internal static class NetworkConfig
{
    private static readonly XElement settings = XElement.Load(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "network.xml"));
    internal static readonly IPAddress Address = IPAddress.Parse((string)settings.Element("Address"));
    private static readonly int Prefix = (int)settings.Element("Prefix");

    internal static bool Allows(IPAddress peer)
    {
        if (Prefix < 1 || Prefix > 32 || peer.AddressFamily != AddressFamily.InterNetwork || Address.AddressFamily != AddressFamily.InterNetwork) return false;
        byte[] local = Address.GetAddressBytes(), remote = peer.GetAddressBytes();
        for (int bit = 0; bit < Prefix; bit++)
            if (((local[bit / 8] ^ remote[bit / 8]) & (128 >> (bit % 8))) != 0) return false;
        return true;
    }
}
