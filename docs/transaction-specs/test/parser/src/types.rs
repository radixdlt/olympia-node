extern crate bytebuffer;
extern crate hex;
extern crate primitive_types;

use bytebuffer::ByteBuffer;
use std::fmt;
use std::str;

/// Boolean
#[derive(Debug)]
pub struct Boolean {
    pub raw: u8,
}

pub type Size = u16;

/// Unsigned 256-bit integer
#[derive(Debug)]
pub struct U256 {
    pub raw: primitive_types::U256,
}

/// Byte array of variable length
pub struct Bytes {
    pub length: Size,
    pub data: Vec<u8>,
}

/// UTF-8 string
#[derive(Debug)]
pub struct UTF8 {
    pub length: Size,
    pub data: String,
}

/// Hash value
pub struct Hash {
    pub raw: [u8; 32],
}

/// ECDSA public key
pub struct PublicKey {
    pub raw: [u8; 33],
}

/// ECDSA signature
pub struct Signature {
    pub v: u8,
    pub r: [u8; 32],
    pub s: [u8; 32],
}

/// Substate ID
#[derive(Debug)]
pub struct SubstateId {
    pub hash: Hash,
    pub index: u32,
}

pub type SubstateIndex = u16;

/// Virtual Substate ID
pub struct VirtualSubstateID {
    pub length: Size,
    pub data: Vec<u8>,
}

/// Virtual Substate ID
pub struct LocalVirtualSubstateID {
    pub length: Size,
    pub data: Vec<u8>,
}

/// Radix Engine address
pub enum Address {
    System,

    RadixNativeToken,

    HashedKeyNonce([u8; 26]),

    PublicKey([u8; 33]),
}

#[macro_export]
macro_rules! read_bytes {
    ($buffer: expr, $size: expr) => {{
        let mut bytes = [0u8; $size];
        let temp = $buffer.read_bytes(bytes.len());
        bytes.copy_from_slice(&temp[..]);
        bytes
    }};
}

impl Boolean {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let raw = buffer.read_u8();
        if raw != 0 && raw != 1 {
            panic!("Invalid boolean value: {}", raw);
        }
        Self { raw }
    }
}

impl U256 {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        Self {
            raw: primitive_types::U256::from_big_endian(&buffer.read_bytes(32)),
        }
    }
}

impl Bytes {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let length = buffer.read_u16();
        let data = buffer.read_bytes(length as usize);
        Self { length, data }
    }
}

impl UTF8 {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let length = buffer.read_u16();
        let data = buffer.read_bytes(length as usize);
        Self {
            length,
            data: str::from_utf8(&data).unwrap().to_owned(),
        }
    }
}

impl Hash {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        Self {
            raw: read_bytes!(buffer, 32),
        }
    }
}

impl PublicKey {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        Self {
            raw: read_bytes!(buffer, 33),
        }
    }
}

impl Signature {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        Self {
            v: buffer.read_u8(),
            r: read_bytes!(buffer, 32),
            s: read_bytes!(buffer, 32),
        }
    }
}

impl SubstateId {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        Self {
            hash: Hash::from_buffer(buffer),
            index: buffer.read_u32(),
        }
    }
}

impl VirtualSubstateID {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let length = buffer.read_u16();
        let data = buffer.read_bytes(length as usize);
        Self { length, data }
    }
}

impl LocalVirtualSubstateID {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let length = buffer.read_u16();
        let data = buffer.read_bytes(length as usize);
        Self { length, data }
    }
}

impl Address {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let t = buffer.read_u8();
        match t {
            0x00 => Self::System,
            0x01 => Self::RadixNativeToken,
            0x03 => Address::HashedKeyNonce(read_bytes!(buffer, 26)),
            0x04 => Address::PublicKey(read_bytes!(buffer, 33)),
            _ => panic!("Invalid address type: {}", t),
        }
    }
}

impl fmt::Debug for Address {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::System => write!(f, "0x00"),
            Self::RadixNativeToken => write!(f, "0x01"),
            Self::HashedKeyNonce(data) => write!(f, "0x03{}", hex::encode(data)),
            Self::PublicKey(data) => write!(f, "0x04{}", hex::encode(data)),
        }
    }
}

impl fmt::Debug for Bytes {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "0x{}", hex::encode(&self.data))
    }
}

impl fmt::Debug for Hash {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "0x{}", hex::encode(self.raw))
    }
}

impl fmt::Debug for PublicKey {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "0x{}", hex::encode(self.raw))
    }
}

impl fmt::Debug for Signature {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "0x{:02x?}{}{}",
            self.v,
            hex::encode(self.r),
            hex::encode(self.s)
        )
    }
}

impl fmt::Debug for VirtualSubstateID {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "0x{}", hex::encode(&self.data))
    }
}

impl fmt::Debug for LocalVirtualSubstateID {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "0x{}", hex::encode(&self.data))
    }
}
