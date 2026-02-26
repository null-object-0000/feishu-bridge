# Privacy Policy

## Data Collection

This plugin does not collect, store, or transmit any personal data on its own. It acts as a pass-through layer between the newbie-feishu gateway and Dify workflows.

## Data Flow

1. Feishu events are received by the newbie-feishu Java gateway via WebSocket.
2. The gateway forwards event data to this plugin's webhook endpoint.
3. The plugin parses the event and passes structured variables to the Dify workflow.

All data processing occurs within your self-hosted Dify instance. No data is sent to any third-party service by this plugin.

## Third-Party Services

- **Feishu (飞书)**: Events originate from Feishu's platform. Refer to [Feishu's privacy policy](https://www.feishu.cn/privacy) for details.
- **newbie-feishu Gateway**: A self-hosted service you control. No external data sharing.
