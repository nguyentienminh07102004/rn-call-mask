import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  PermissionsAndroid,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import {
  CallMask,
  type CallCapabilities,
  type CallEvent,
  type CallSession,
} from '../src';

type Slot = 'A' | 'B';

function Button({
  title,
  onPress,
  disabled = false,
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={[styles.button, disabled && styles.buttonDisabled]}
    >
      <Text style={styles.buttonText}>{title}</Text>
    </Pressable>
  );
}

export default function App() {
  const [calls, setCalls] = useState<CallSession[]>([]);
  const [events, setEvents] = useState<CallEvent[]>([]);
  const [capabilities, setCapabilities] = useState<CallCapabilities | null>(null);
  const [slotIds, setSlotIds] = useState<Partial<Record<Slot, string>>>({});
  const [error, setError] = useState<string | null>(null);

  const appendEvent = useCallback((event: CallEvent) => {
    setEvents(current => [event, ...current].slice(0, 30));
  }, []);

  const refresh = useCallback(async () => {
    const [nextCalls, nextCapabilities] = await Promise.all([
      CallMask.getCalls(),
      CallMask.getCapabilities(),
    ]);
    setCalls(nextCalls);
    setCapabilities(nextCapabilities);
  }, []);

  useEffect(() => {
    const subscription = CallMask.addEventListener(event => {
      appendEvent(event);
      void refresh();
    });

    void CallMask.consumePendingEvents()
      .then(pending => {
        pending.forEach(appendEvent);
        return refresh();
      })
      .catch(value => setError(String(value)));

    return () => subscription.remove();
  }, [appendEvent, refresh]);

  const requestNotifications = useCallback(async () => {
    if (Platform.OS === 'android' && Platform.Version >= 33) {
      await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
      );
    }
    await refresh();
  }, [refresh]);

  const show = useCallback(
    async (slot: Slot) => {
      try {
        setError(null);
        const callId = `demo-${slot}-${Date.now()}`;
        setSlotIds(current => ({ ...current, [slot]: callId }));
        await CallMask.showIncomingCall({
          callId,
          media: slot === 'A' ? 'audio' : 'video',
          caller: {
            id: `caller-${slot}`,
            name: `Caller ${slot}`,
            handle: `user-${slot.toLowerCase()}`,
          },
          data: { slot },
        });
        await refresh();
      } catch (value) {
        setError(String(value));
      }
    },
    [refresh],
  );

  const act = useCallback(
    async (
      slot: Slot,
      action: 'answer' | 'decline' | 'end' | 'silence' | 'active' | 'dismiss',
    ) => {
      const callId = slotIds[slot];
      if (!callId) return;
      try {
        setError(null);
        if (action === 'answer') await CallMask.answer(callId);
        if (action === 'decline') await CallMask.decline(callId);
        if (action === 'end') await CallMask.end(callId);
        if (action === 'silence') await CallMask.silence(callId);
        if (action === 'active') await CallMask.markActive(callId);
        if (action === 'dismiss') await CallMask.dismissIncomingUI(callId);
        await refresh();
      } catch (value) {
        setError(String(value));
      }
    },
    [refresh, slotIds],
  );

  const ringing = useMemo(
    () => calls.filter(call => call.state === 'ringing' || call.state === 'incoming'),
    [calls],
  );

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>RN Call Mask device harness</Text>
        <Text style={styles.help}>
          Exercise Call A/B isolation, notification/full-screen fallback, native
          actions, and cold-start pending event replay.
        </Text>

        <View style={styles.card}>
          <Text style={styles.heading}>Capabilities</Text>
          <Text>{JSON.stringify(capabilities, null, 2)}</Text>
          <View style={styles.row}>
            <Button title="Request notification permission" onPress={() => void requestNotifications()} />
            <Button title="Refresh" onPress={() => void refresh()} />
          </View>
          {Platform.OS === 'android' && Platform.Version >= 34 ? (
            <Button title="Open full-screen settings" onPress={() => void CallMask.openFullScreenSettings()} />
          ) : null}
        </View>

        {(['A', 'B'] as const).map(slot => (
          <View style={styles.card} key={slot}>
            <Text style={styles.heading}>Call {slot}</Text>
            <Text selectable>{slotIds[slot] ?? 'not created'}</Text>
            <View style={styles.row}>
              <Button title={`Show ${slot}`} onPress={() => void show(slot)} />
              <Button title="Answer" disabled={!slotIds[slot]} onPress={() => void act(slot, 'answer')} />
              <Button title="Active" disabled={!slotIds[slot]} onPress={() => void act(slot, 'active')} />
              <Button title="Silence" disabled={!slotIds[slot]} onPress={() => void act(slot, 'silence')} />
              <Button title="Dismiss UI" disabled={!slotIds[slot]} onPress={() => void act(slot, 'dismiss')} />
              <Button title="Decline" disabled={!slotIds[slot]} onPress={() => void act(slot, 'decline')} />
              <Button title="End" disabled={!slotIds[slot]} onPress={() => void act(slot, 'end')} />
            </View>
          </View>
        ))}

        <View style={styles.card}>
          <Text style={styles.heading}>Current calls ({calls.length})</Text>
          <Text>Ringing: {ringing.length}</Text>
          {calls.map(call => (
            <Text selectable key={call.callId} style={styles.mono}>
              {call.callId} · {call.state} · {call.caller.name}
            </Text>
          ))}
        </View>

        <View style={styles.card}>
          <Text style={styles.heading}>Events ({events.length})</Text>
          <Button
            title="Consume pending events"
            onPress={() =>
              void CallMask.consumePendingEvents().then(next => {
                next.forEach(appendEvent);
                return refresh();
              })
            }
          />
          {events.map(event => (
            <Text selectable key={event.eventId} style={styles.mono}>
              {event.type} · {event.callId} · {new Date(event.timestamp).toLocaleTimeString()}
            </Text>
          ))}
        </View>

        {error ? (
          <View style={styles.errorCard}>
            <Text selectable>{error}</Text>
          </View>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  container: { padding: 16, gap: 12 },
  title: { fontSize: 24, fontWeight: '700' },
  help: { fontSize: 14, lineHeight: 20 },
  card: { padding: 14, borderWidth: StyleSheet.hairlineWidth, borderRadius: 10, gap: 8 },
  errorCard: { padding: 14, borderWidth: 1, borderRadius: 10 },
  heading: { fontSize: 18, fontWeight: '600' },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  button: { paddingHorizontal: 12, paddingVertical: 10, borderWidth: 1, borderRadius: 8 },
  buttonDisabled: { opacity: 0.4 },
  buttonText: { fontWeight: '600' },
  mono: { fontFamily: Platform.select({ android: 'monospace', default: undefined }) },
});
