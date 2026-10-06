# LAN sync protocol for the desktop companion

The Android client and Windows desktop companion use this contract for local inventory synchronization and photo transfer.

## Connection rules

- The phone is configured with a private IPv4 address and port for LAN sync, or `127.0.0.1` and the forwarded port for a USB ADB reverse tunnel, plus a pairing token.
- For LAN sync, the Android client only sends requests when the desktop address is private and is on the active Wi-Fi or Ethernet subnet. For USB sync, ADB must be connected and have an active reverse tunnel. Neither mode uses a cloud relay.
- The desktop server should bind only to its local network interfaces, require the pairing token, and be protected by the host firewall. Do not configure router port forwarding.
- Barcode catalog lookups remain separate and may use their existing HTTPS internet APIs.

## Photo transfer

Phone-selected photos are resized and encoded as JPEG. During sync, the Android client uploads each pending item's photo to `POST /api/v1/photos/<sync-id>` using the same `Authorization: Bearer <pairing-token>` header and a JSON body shaped like `{"mimeType":"image/jpeg","data":"<base64>"}`. The desktop stores the binary photo in `%LOCALAPPDATA%\\InventoryTracker\\photos\\<sync-id>.jpg` (maximum 10 MB). The sync record uses `imageUrl: "inventory-photo://<sync-id>"` to refer to that photo. A paired Android client downloads it through authenticated `GET /api/v1/photos/<sync-id>` when it does not already have a local copy. Photo transfer uses the same authenticated LAN or USB transport as database sync.

## Desktop pairing QR code

The desktop app can display a QR code to pair the Android client. Encode this UTF-8 JSON:

```json
{"type":"inventorytracker-sync","version":1,"address":"192.168.1.20:8765","token":"<pairing-token>"}
```

For LAN pairing, `address` is the desktop's private IPv4 address and port. For USB pairing, it is `127.0.0.1:8765`; ADB reverse-forwards that phone loopback port to the desktop server over USB. The desktop UI creates the tunnel and QR. The Android Sync screen's **Scan desktop pairing QR** action reads either payload and stores the pairing credentials. An equivalent LAN URI is also accepted: `inventorytracker://pair?address=192.168.1.20%3A8765&token=<url-encoded-token>`.

## Request

While the paired Android app is in the foreground, it long-polls authenticated `GET /api/v1/trigger`. The desktop **Sync** action queues a full desktop change feed and signals this endpoint; on a positive response the phone sends the sync request below. If the app is closed or unreachable, the desktop feed stays queued for the next manual or scheduled phone sync.

`POST http://<desktop-private-ip>:<port>/api/v1/sync`

Header: `Authorization: Bearer <pairing-token>`

```json
{
  "deviceId": "stable-client-uuid",
  "cursor": 0,
  "changes": [
    {
      "id": "stable-record-uuid",
      "name": "Craft paper",
      "barcode": "012345678905",
      "brand": "MakerCo",
      "quantity": 2,
      "category": "Crafts",
      "type": "Materials",
      "imageUrl": null,
      "location": "Shelf A",
      "notes": null,
      "price": 4.99,
      "expirationDate": null,
      "updatedAt": 1790900000000,
      "version": 0,
      "deleted": false
    }
  ]
}
```

`changes` contains records changed locally since the previous successful sync, including deleted records (`deleted: true`). IDs are stable across devices; barcodes are not record IDs and can be duplicated.

## Response

Return HTTP 200 with JSON shaped like:

```json
{
  "records": [],
  "acknowledgedIds": ["stable-record-uuid"],
  "cursor": 1
}
```

`records` contains the desktop's merged canonical versions of records changed after the request cursor, including tombstones. The desktop must resolve concurrent edits consistently, assign an increasing `version` to accepted changes, include accepted client changes in `acknowledgedIds`, and include their canonical records in `records`. `cursor` is the new per-database change cursor. The mobile client only advances its cursor after a successful response.
