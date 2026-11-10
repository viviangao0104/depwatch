import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { auth } from './client'

/**
  * Singleton STOMP connection. The token goes to the gateway via ?token= (the browser WebSocket API can't set headers).
  * Reconnection is handled by stompjs (reconnectDelay).
 */
class LiveClient {
  private client: Client | null = null
  private pending: Array<() => void> = []

  private ensure(): Client {
    if (this.client) return this.client
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${proto}://${location.host}/ws?token=${encodeURIComponent(auth.token() ?? '')}`,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.pending.forEach((fn) => fn())
        this.pending = []
      },
      onStompError: (f) => console.warn('STOMP error', f.headers['message']),
    })
    client.activate()
    this.client = client
    return client
  }

  subscribe<T>(destination: string, handler: (payload: T) => void): () => void {
    const client = this.ensure()
    let sub: StompSubscription | null = null
    const doSub = () => {
      sub = client.subscribe(destination, (msg: IMessage) => handler(JSON.parse(msg.body) as T))
    }
    if (client.connected) doSub()
    else this.pending.push(doSub)
    return () => {
      sub?.unsubscribe()
      this.pending = this.pending.filter((p) => p !== doSub)
    }
  }

  disconnect() {
    this.client?.deactivate()
    this.client = null
    this.pending = []
  }
}

export const live = new LiveClient()
