extern crate bytebuffer;

use bytebuffer::ByteBuffer;
use core::fmt::Debug;

use crate::types::Address;
use crate::types::Boolean;
use crate::types::PublicKey;
use crate::types::U256;
use crate::types::UTF8;

pub trait Substate: std::fmt::Debug {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self
    where
        Self: Sized;
}

#[derive(Debug)]
pub struct TokenResource {
    pub reserved: u8,
    pub resource: Address,
    pub granularity: U256,
    pub is_mutable: Boolean,
    pub minter: Option<PublicKey>,
}

#[derive(Debug)]
pub struct TokenResourceMetadata {
    pub reserved: u8,
    pub resource: Address,
    pub name: UTF8,
    pub description: UTF8,
    pub url: UTF8,
    pub icon_url: UTF8,
}

#[derive(Debug)]
pub struct Tokens {
    pub reserved: u8,
    pub owner: Address,
    pub resource: Address,
    pub amount: U256,
}

#[derive(Debug)]
pub struct PreparedStake {
    pub reserved: u8,
    pub validator: PublicKey,
    pub owner: Address,
    pub amount: U256,
}

#[derive(Debug)]
pub struct StakeOwnership {
    pub reserved: u8,
    pub validator: PublicKey,
    pub owner: Address,
    pub amount: U256,
}

#[derive(Debug)]
pub struct PreparedUnstake {
    pub reserved: u8,
    pub validator: PublicKey,
    pub owner: Address,
    pub amount: U256,
}

#[derive(Debug)]
pub struct ExitingStake {
    pub reserved: u8,
    pub epoch_unlocked: u64,
    pub validator: PublicKey,
    pub amount: U256,
    pub ownership: U256,
}

#[derive(Debug)]
pub struct ValidatorMetadata {
    pub reserved: u8,
    pub validator: PublicKey,
    pub name: UTF8,
    pub url: UTF8,
}

#[derive(Debug)]
pub struct ValidatorAllowDelegationFlag {
    pub reserved: u8,
    pub validator: PublicKey,
    pub is_delegation_allowed: Boolean,
}

#[derive(Debug)]
pub struct ValidatorRegisteredFlagCopy {
    pub reserved: u8,
    pub update_epoch: Option<u64>,
    pub validator: PublicKey,
    pub is_registered: Boolean,
}

#[derive(Debug)]
pub struct ValidatorRakeCopy {
    pub reserved: u8,
    pub update_epoch: Option<u64>,
    pub validator: PublicKey,
    pub rake: u32,
}

#[derive(Debug)]
pub struct ValidatorOwnerCopy {
    pub reserved: u8,
    pub update_epoch: Option<u64>,
    pub validator: PublicKey,
    pub owner: Address,
}

fn read_reserved_byte(buffer: &mut ByteBuffer) -> u8 {
    let reserved = buffer.read_u8();
    if reserved != 0 {
        panic!("Reserved byte should be zero, actual: {}", reserved);
    }
    reserved
}

impl Substate for TokenResource {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let reserved = read_reserved_byte(buffer);
        let resource = Address::from_buffer(buffer);
        let granularity = U256::from_buffer(buffer);
        let is_mutable = Boolean::from_buffer(buffer);
        let minter = match Boolean::from_buffer(buffer).raw {
            0x01 => Some(PublicKey::from_buffer(buffer)),
            _ => None,
        };

        Self {
            reserved,
            resource,
            granularity,
            is_mutable,
            minter,
        }
    }
}

impl Substate for TokenResourceMetadata {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let reserved = read_reserved_byte(buffer);
        let resource = Address::from_buffer(buffer);
        let name = UTF8::from_buffer(buffer);
        let description = UTF8::from_buffer(buffer);
        let url = UTF8::from_buffer(buffer);
        let icon_url = UTF8::from_buffer(buffer);

        Self {
            reserved,
            resource,
            name,
            description,
            url,
            icon_url,
        }
    }
}

impl Substate for Tokens {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let reserved = read_reserved_byte(buffer);
        let owner = Address::from_buffer(buffer);
        let resource = Address::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);

        Self {
            reserved,
            resource,
            owner,
            amount,
        }
    }
}

impl Substate for PreparedStake {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let reserved = read_reserved_byte(buffer);
        let validator = PublicKey::from_buffer(buffer);
        let owner = Address::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);

        Self {
            reserved,
            owner,
            validator,
            amount,
        }
    }
}

impl Substate for StakeOwnership {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let reserved = read_reserved_byte(buffer);
        let validator = PublicKey::from_buffer(buffer);
        let owner = Address::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);

        Self {
            reserved,
            validator,
            owner,
            amount,
        }
    }
}

impl Substate for PreparedUnstake {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let reserved = read_reserved_byte(buffer);
        let validator = PublicKey::from_buffer(buffer);
        let owner = Address::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);

        Self {
            reserved,
            validator,
            owner,
            amount,
        }
    }
}

impl Substate for ExitingStake {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let reserved = read_reserved_byte(buffer);
        let epoch_unlocked = buffer.read_u64();
        let validator = PublicKey::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);
        let ownership = U256::from_buffer(buffer);

        Self {
            reserved,
            epoch_unlocked,
            validator,
            amount,
            ownership,
        }
    }
}

impl Substate for ValidatorMetadata {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let reserved = read_reserved_byte(buffer);
        let validator = PublicKey::from_buffer(buffer);
        let name = UTF8::from_buffer(buffer);
        let url = UTF8::from_buffer(buffer);

        Self {
            reserved,
            validator,
            name,
            url,
        }
    }
}

impl Substate for ValidatorAllowDelegationFlag {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let reserved = read_reserved_byte(buffer);
        let validator = PublicKey::from_buffer(buffer);
        let is_delegation_allowed = Boolean::from_buffer(buffer);

        Self {
            reserved,
            validator,
            is_delegation_allowed,
        }
    }
}

impl Substate for ValidatorRegisteredFlagCopy {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let reserved = read_reserved_byte(buffer);
        let update_epoch = match Boolean::from_buffer(buffer).raw {
            0x01 => Some(buffer.read_u64()),
            _ => None,
        };
        let validator = PublicKey::from_buffer(buffer);
        let is_registered = Boolean::from_buffer(buffer);

        Self {
            reserved,
            update_epoch,
            validator,
            is_registered,
        }
    }
}

impl Substate for ValidatorRakeCopy {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let reserved = read_reserved_byte(buffer);
        let update_epoch = match Boolean::from_buffer(buffer).raw {
            0x01 => Some(buffer.read_u64()),
            _ => None,
        };
        let validator = PublicKey::from_buffer(buffer);
        let rake = buffer.read_u32();

        Self {
            reserved,
            update_epoch,
            validator,
            rake,
        }
    }
}

impl Substate for ValidatorOwnerCopy {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let reserved = read_reserved_byte(buffer);
        let update_epoch = match Boolean::from_buffer(buffer).raw {
            0x01 => Some(buffer.read_u64()),
            _ => None,
        };
        let validator = PublicKey::from_buffer(buffer);
        let owner = Address::from_buffer(buffer);

        Self {
            reserved,
            update_epoch,
            validator,
            owner,
        }
    }
}
