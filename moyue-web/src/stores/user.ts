import { defineStore } from 'pinia';
import { ref } from 'vue';

const TOKEN_KEY = 'moyue_token';
const USER_KEY = 'moyue_user';

function parseSafe(value: string | null): Record<string, unknown> | null {
  if (!value) {
    return null;
  }
  try {
    return JSON.parse(value) as Record<string, unknown>;
  } catch {
    return null;
  }
}

/**
 * 用户状态：token 与 userInfo。
 * 持久化到 localStorage，登录态在刷新后保留。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '');
  const userInfo = ref<Record<string, unknown> | null>(parseSafe(localStorage.getItem(USER_KEY)));

  function setToken(value: string) {
    token.value = value;
    if (value) {
      localStorage.setItem(TOKEN_KEY, value);
    } else {
      localStorage.removeItem(TOKEN_KEY);
    }
  }

  function setUserInfo(value: Record<string, unknown> | null) {
    userInfo.value = value;
    if (value) {
      localStorage.setItem(USER_KEY, JSON.stringify(value));
    } else {
      localStorage.removeItem(USER_KEY);
    }
  }

  function logout() {
    setToken('');
    setUserInfo(null);
  }

  return { token, userInfo, setToken, setUserInfo, logout };
});
