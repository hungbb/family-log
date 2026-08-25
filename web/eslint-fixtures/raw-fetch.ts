// EXPECT: no-restricted-globals
// Every request goes through the wrapper, which owns error mapping and the correlation id.
export async function loadClans() {
  const response = await fetch('/api/v1/clans');
  return response.json();
}
