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
pub struct REAddress {
    pub address: Address,
}

#[derive(Debug)]
pub struct TokenDefinition {
    pub resource: Address,
    pub token_type: u8,
    pub supply: Option<U256>,
    pub minter: Option<PublicKey>,
    pub name: UTF8,
    pub description: UTF8,
    pub url: UTF8,
    pub icon_url: UTF8,
}

#[derive(Debug)]
pub struct Tokens {
    pub resource: Address,
    pub owner: Address,
    pub amount: U256,
}

#[derive(Debug)]
pub struct PreparedStake {
    pub owner: Address,
    pub validator: PublicKey,
    pub amount: U256,
}

#[derive(Debug)]
pub struct StakeOwnership {
    pub validator: PublicKey,
    pub owner: Address,
    pub amount: U256,
}

#[derive(Debug)]
pub struct PreparedUnstake {
    pub validator: PublicKey,
    pub owner: Address,
    pub amount: U256,
}

#[derive(Debug)]
pub struct ExitingStake {
    pub epoch_unlocked: u64,
    pub validator: PublicKey,
    pub amount: U256,
    pub ownership: U256,
}

#[derive(Debug)]
pub struct ValidatorRegisteredFlagCopy {
    pub validator: PublicKey,
    pub is_registered: Boolean,
}

#[derive(Debug)]
pub struct PreparedRegisteredFlagUpdate {
    pub validator: PublicKey,
    pub is_delegation_allowed: Boolean,
}

#[derive(Debug)]
pub struct ValidatorMetadata {
    pub validator: PublicKey,
    pub name: UTF8,
    pub url: UTF8,
}

#[derive(Debug)]
pub struct ValidatorAllowDelegationFlag {
    pub validator: PublicKey,
    pub is_delegation_allowed: Boolean,
}

#[derive(Debug)]
pub struct ValidatorOwnerCopy {
    pub validator: PublicKey,
    pub owner: Address,
}

impl Substate for REAddress {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        Self {
            address: Address::from_buffer(buffer),
        }
    }
}

impl Substate for TokenDefinition {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let resource = Address::from_buffer(buffer);
        let token_type = buffer.read_u8();
        let supply = match token_type {
            0x02 => Some(U256::from_buffer(buffer)),
            _ => None,
        };
        let minter = match token_type {
            0x01 => Some(PublicKey::from_buffer(buffer)),
            _ => None,
        };
        let name = UTF8::from_buffer(buffer);
        let description = UTF8::from_buffer(buffer);
        let url = UTF8::from_buffer(buffer);
        let icon_url = UTF8::from_buffer(buffer);

        Self {
            resource,
            token_type,
            supply,
            minter,
            name,
            description,
            url,
            icon_url,
        }
    }
}

impl Substate for Tokens {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let resource = Address::from_buffer(buffer);
        let owner = Address::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);

        Self {
            resource,
            owner,
            amount,
        }
    }
}

impl Substate for PreparedStake {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let owner = Address::from_buffer(buffer);
        let validator = PublicKey::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);

        Self {
            owner,
            validator,
            amount,
        }
    }
}

impl Substate for StakeOwnership {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let validator = PublicKey::from_buffer(buffer);
        let owner = Address::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);

        Self {
            validator,
            owner,
            amount,
        }
    }
}

impl Substate for PreparedUnstake {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let validator = PublicKey::from_buffer(buffer);
        let owner = Address::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);

        Self {
            validator,
            owner,
            amount,
        }
    }
}

impl Substate for ExitingStake {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let epoch_unlocked = buffer.read_u64();
        let validator = PublicKey::from_buffer(buffer);
        let amount = U256::from_buffer(buffer);
        let ownership = U256::from_buffer(buffer);

        Self {
            epoch_unlocked,
            validator,
            amount,
            ownership,
        }
    }
}

impl Substate for ValidatorRegisteredFlagCopy {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let validator = PublicKey::from_buffer(buffer);
        let is_registered = Boolean::from_buffer(buffer);

        Self {
            validator,
            is_registered,
        }
    }
}

impl Substate for ValidatorMetadata {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let validator = PublicKey::from_buffer(buffer);
        let name = UTF8::from_buffer(buffer);
        let url = UTF8::from_buffer(buffer);

        Self {
            validator,
            name,
            url,
        }
    }
}

impl Substate for ValidatorAllowDelegationFlag {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let validator = PublicKey::from_buffer(buffer);
        let is_delegation_allowed = Boolean::from_buffer(buffer);

        Self {
            validator,
            is_delegation_allowed,
        }
    }
}

impl Substate for PreparedRegisteredFlagUpdate {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let validator = PublicKey::from_buffer(buffer);
        let is_delegation_allowed = Boolean::from_buffer(buffer);

        Self {
            validator,
            is_delegation_allowed,
        }
    }
}

impl Substate for ValidatorOwnerCopy {
    fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let validator = PublicKey::from_buffer(buffer);
        let owner = Address::from_buffer(buffer);

        Self { validator, owner }
    }
}
