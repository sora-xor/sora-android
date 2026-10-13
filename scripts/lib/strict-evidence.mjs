import { createHash } from "node:crypto";
import {
  closeSync,
  constants as fsConstants,
  fstatSync,
  lstatSync,
  openSync,
  readSync,
  realpathSync,
} from "node:fs";
import { isAbsolute, resolve } from "node:path";

const canonicalAbsolutePath = (path) =>
  typeof path === "string" &&
  path.length > 0 &&
  isAbsolute(path) &&
  resolve(path) === path;

const openStableRegularFile = (path, maximumBytes) => {
  if (
    !canonicalAbsolutePath(path) ||
    !Number.isSafeInteger(maximumBytes) ||
    maximumBytes < 1
  ) {
    return null;
  }
  let descriptor = null;
  try {
    descriptor = openSync(path, fsConstants.O_RDONLY | fsConstants.O_NOFOLLOW);
    const descriptorStat = fstatSync(descriptor);
    const pathStat = lstatSync(path);
    if (
      !descriptorStat.isFile() ||
      descriptorStat.size < 1 ||
      descriptorStat.size > maximumBytes ||
      !pathStat.isFile() ||
      pathStat.isSymbolicLink() ||
      pathStat.dev !== descriptorStat.dev ||
      pathStat.ino !== descriptorStat.ino ||
      realpathSync(path) !== path
    ) {
      closeSync(descriptor);
      return null;
    }
    return { descriptor, before: descriptorStat };
  } catch {
    if (descriptor !== null) {
      try {
        closeSync(descriptor);
      } catch {
        // The evidence remains rejected.
      }
    }
    return null;
  }
};

const closeDescriptor = (descriptor) => {
  try {
    closeSync(descriptor);
  } catch {
    // A failed close cannot turn rejected evidence into accepted evidence.
  }
};

const stableAfterRead = (path, descriptor, before, bytesRead) => {
  try {
    const after = fstatSync(descriptor);
    const pathAfter = lstatSync(path);
    return (
      bytesRead === before.size &&
      after.isFile() &&
      after.dev === before.dev &&
      after.ino === before.ino &&
      after.size === before.size &&
      after.mtimeMs === before.mtimeMs &&
      after.ctimeMs === before.ctimeMs &&
      pathAfter.isFile() &&
      !pathAfter.isSymbolicLink() &&
      pathAfter.dev === before.dev &&
      pathAfter.ino === before.ino &&
      realpathSync(path) === path
    );
  } catch {
    return false;
  }
};

export const parseStrictJsonBytes = (bytes) => {
  const source = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  let index = 0;
  const invalid = () => {
    throw new Error("STRICT_JSON_INVALID");
  };
  const skipWhitespace = () => {
    while (
      source[index] === " " ||
      source[index] === "\t" ||
      source[index] === "\n" ||
      source[index] === "\r"
    ) {
      index += 1;
    }
  };
  const parseString = () => {
    if (source[index] !== '"') invalid();
    const start = index;
    index += 1;
    while (index < source.length) {
      const character = source[index];
      if (character === '"') {
        index += 1;
        const value = JSON.parse(source.slice(start, index));
        for (const scalar of value) {
          const codePoint = scalar.codePointAt(0);
          if (codePoint >= 0xd800 && codePoint <= 0xdfff) invalid();
        }
        return value;
      }
      if (character === "\\") {
        index += 1;
        const escape = source[index];
        if (escape === "u") {
          if (!/^[0-9a-fA-F]{4}$/.test(source.slice(index + 1, index + 5))) {
            invalid();
          }
          index += 5;
          continue;
        }
        if (!['"', "\\", "/", "b", "f", "n", "r", "t"].includes(escape)) {
          invalid();
        }
        index += 1;
        continue;
      }
      if (character.charCodeAt(0) < 0x20) invalid();
      index += 1;
    }
    invalid();
  };
  const parseNumber = () => {
    const match = /^(?:0|-?[1-9][0-9]*)/.exec(source.slice(index));
    if (match === null) invalid();
    index += match[0].length;
    const value = Number(match[0]);
    if (!Number.isSafeInteger(value)) invalid();
    return value;
  };
  const parseValue = () => {
    skipWhitespace();
    const character = source[index];
    if (character === '"') return parseString();
    if (character === "{") {
      index += 1;
      skipWhitespace();
      const value = Object.create(null);
      const keys = new Set();
      if (source[index] === "}") {
        index += 1;
        return value;
      }
      while (index < source.length) {
        skipWhitespace();
        const key = parseString();
        if (keys.has(key)) invalid();
        keys.add(key);
        skipWhitespace();
        if (source[index] !== ":") invalid();
        index += 1;
        value[key] = parseValue();
        skipWhitespace();
        if (source[index] === "}") {
          index += 1;
          return value;
        }
        if (source[index] !== ",") invalid();
        index += 1;
      }
      invalid();
    }
    if (character === "[") {
      index += 1;
      skipWhitespace();
      const value = [];
      if (source[index] === "]") {
        index += 1;
        return value;
      }
      while (index < source.length) {
        value.push(parseValue());
        skipWhitespace();
        if (source[index] === "]") {
          index += 1;
          return value;
        }
        if (source[index] !== ",") invalid();
        index += 1;
      }
      invalid();
    }
    if (source.startsWith("true", index)) {
      index += 4;
      return true;
    }
    if (source.startsWith("false", index)) {
      index += 5;
      return false;
    }
    if (source.startsWith("null", index)) {
      index += 4;
      return null;
    }
    return parseNumber();
  };
  const value = parseValue();
  skipWhitespace();
  if (index !== source.length) invalid();
  return value;
};

export const readStrictJsonFile = (path, maximumBytes) => {
  const opened = openStableRegularFile(path, maximumBytes);
  if (opened === null) return null;
  const { descriptor, before } = opened;
  try {
    const chunks = [];
    let totalBytes = 0;
    while (true) {
      const remaining = maximumBytes - totalBytes + 1;
      const chunk = Buffer.allocUnsafe(Math.min(64 * 1024, remaining));
      const count = readSync(descriptor, chunk, 0, chunk.length, null);
      if (count === 0) break;
      chunks.push(chunk.subarray(0, count));
      totalBytes += count;
      if (totalBytes > maximumBytes) return null;
    }
    if (!stableAfterRead(path, descriptor, before, totalBytes)) return null;
    const bytes = Buffer.concat(chunks, totalBytes);
    return {
      bytes,
      byteCount: totalBytes,
      sha256: createHash("sha256").update(bytes).digest("hex"),
      value: parseStrictJsonBytes(bytes),
    };
  } catch {
    return null;
  } finally {
    closeDescriptor(descriptor);
  }
};

export const hashStableRegularFile = (path, maximumBytes) => {
  const opened = openStableRegularFile(path, maximumBytes);
  if (opened === null) return null;
  const { descriptor, before } = opened;
  try {
    const digest = createHash("sha256");
    const buffer = Buffer.allocUnsafe(1024 * 1024);
    let totalBytes = 0;
    while (true) {
      const count = readSync(descriptor, buffer, 0, buffer.length, null);
      if (count === 0) break;
      digest.update(buffer.subarray(0, count));
      totalBytes += count;
      if (totalBytes > maximumBytes) return null;
    }
    if (!stableAfterRead(path, descriptor, before, totalBytes)) return null;
    return { bytes: totalBytes, sha256: digest.digest("hex") };
  } catch {
    return null;
  } finally {
    closeDescriptor(descriptor);
  }
};
