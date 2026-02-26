import type {
	IDataObject,
	INodeType,
	INodeTypeDescription,
	IWebhookFunctions,
	IWebhookResponseData,
} from 'n8n-workflow';

export class NewbieFeishuTrigger implements INodeType {
	description: INodeTypeDescription = {
		displayName: 'Newbie Feishu Trigger',
		name: 'newbieFeishuTrigger',
		icon: 'file:newbie-feishu.svg',
		group: ['trigger'],
		version: 1,
		usableAsTool: true,
		subtitle: '={{$parameter["eventType"] || "all events"}}',
		description:
			'Triggers a workflow when a Feishu event is received from the newbie-feishu gateway',
		defaults: {
			name: 'Feishu Event',
		},
		inputs: [],
		outputs: ['main'],
		webhooks: [
			{
				name: 'default',
				httpMethod: 'POST',
				responseMode: 'onReceived',
				path: '={{$parameter["path"]}}',
			},
		],
		properties: [
			{
				displayName: 'Path',
				name: 'path',
				type: 'string',
				default: 'feishu-webhook',
				placeholder: 'feishu-webhook',
				required: true,
				description: 'The webhook path to listen on',
			},
			{
				displayName: 'Event Type Filter',
				name: 'eventType',
				type: 'options',
				default: 'all',
				options: [
					{ name: 'All Events', value: 'all' },
					{ name: 'Message Received (im.message.receive_v1)', value: 'im.message.receive_v1' },
					{ name: 'Card Action', value: 'card_action_trigger' },
				],
				description: 'Filter by specific event type, or receive all events',
			},
			{
				displayName: 'Simplify Output',
				name: 'simplify',
				type: 'boolean',
				default: true,
				description:
					'Whether to extract key fields (sender, message, chat) into top-level properties',
			},
		],
	};

	async webhook(this: IWebhookFunctions): Promise<IWebhookResponseData> {
		const body = this.getBodyData() as IDataObject;
		const eventTypeFilter = this.getNodeParameter('eventType') as string;
		const simplify = this.getNodeParameter('simplify') as boolean;

		const eventType = (body.event_type as string) || '';

		if (eventTypeFilter !== 'all' && eventType !== eventTypeFilter) {
			return { noWebhookResponse: true };
		}

		if (!simplify) {
			return {
				workflowData: [this.helpers.returnJsonArray(body)],
			};
		}

		const payload = (body.payload as IDataObject) || {};
		const event = (payload.event as IDataObject) || payload;
		const header = (payload.header as IDataObject) || {};
		const sender = (event.sender as IDataObject) || {};
		const message = (event.message as IDataObject) || {};

		const outputData: IDataObject = {
			type: body.type,
			event_type: eventType,
			timestamp: body.timestamp,
			event_id: header.event_id,
			sender_id: sender.sender_id,
			message_id: message.message_id,
			chat_id: message.chat_id,
			chat_type: message.chat_type,
			message_type: message.message_type,
			content: message.content,
			create_time: message.create_time,
			raw_payload: payload,
		};

		return {
			workflowData: [this.helpers.returnJsonArray(outputData)],
		};
	}
}
