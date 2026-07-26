# Hardware Validation Matrix

## USB adapters

| Adapter | Preset | Phone/Tablet | OTG Hub | Expected Result | Notes |
|---|---|---|---|---|---|
| FTDI K+DCAN | USB_FTDI_FAST | Android 10+ | Yes | Enumerates, opens, runs DME ID | Preferred baseline |
| FTDI K+DCAN | USB_FTDI_SAFE | Android 10+ | Yes | Slower but stable open/read | Use when frames drop |
| CH340 clone | USB_CH340_SAFE | Android 10+ | Yes | Enumerates, slower reads, more retries | Expect higher latency |
| Ethernet ENET | ETH_ENET | Android 10+ | N/A | TCP connect, DME ID and CAS live read | Check adapter IP first |
| Generic Ethernet OBD | ETH_GENERIC_TCP | Android 10+ | N/A | TCP connect, raw-frame echo or gateway response | Confirm bridge framing |

## Module validation order

1. DME / DDE `ecu_id_9A`
2. DME / DDE `dme_live_basic`
3. CAS `cas_live_terminals`
4. DSC `dsc_live_wheels`
5. EGS `egs_live_basic`
6. SZL `szl_live_switches`
7. KOMBI `kombi_live_drive`
8. FRM / LM `frm_live_status`

## Pass criteria

- Connect/disconnect works 5 times in a row.
- USB permission prompt appears once and open succeeds.
- No app crash when cable is removed during polling.
- Polling stop/start can be repeated without stale state.
- SZL retrofit templates load into coding editor without losing formatting.
- DME, DSC, CAS dashboard cards refresh for 2 minutes without UI freeze.
